"""HTTP-сервис Silero TTS для бота.

POST /tts {"text": "...", "speaker": "xenia"} -> WAV (mono, s16le, 48 кГц); 400 с причиной, если текст
не озвучить. GET /health -> 200, когда модель загружена (сервер слушает порт только после загрузки).
GET /speakers -> голоса модели.
"""
import io
import json
import os
import threading
import time
import wave
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import torch

from normalize import has_speech, normalize

MODEL_PATH = os.environ.get("MODEL_PATH", "/models/model.pt")
DEFAULT_SPEAKER = os.environ.get("DEFAULT_SPEAKER", "xenia")
SAMPLE_RATE = 48000  # ровно столько ждёт Discord

started = time.perf_counter()
model = torch.package.PackageImporter(MODEL_PATH).load_pickle("tts_models", "model")
model.to(torch.device("cpu"))
torch.set_num_threads(int(os.environ.get("TORCH_THREADS", "2")))  # загрузка модели сбрасывает число потоков на 1
# Первый синтез в несколько раз медленнее остальных (~700 мс против ~50): прогреваем до того, как
# откроем порт, — /health отвечает только когда сервис уже быстрый.
model.apply_tts(text="Привет", speaker=DEFAULT_SPEAKER, sample_rate=SAMPLE_RATE)
print(f"model loaded and warmed up in {time.perf_counter() - started:.1f}s, torch {torch.__version__}, "
      f"threads {torch.get_num_threads()}, speakers {model.speakers}", flush=True)

lock = threading.Lock()  # модель не обязана быть потокобезопасной; параллелизм — внутри torch


class BadRequest(Exception):
    pass


def synthesize(text, speaker):
    if speaker not in model.speakers:
        raise BadRequest(f"unknown speaker '{speaker}', known: {model.speakers}")
    prepared = normalize(text)
    if not has_speech(prepared):
        raise BadRequest("nothing to say after normalization")
    with lock:
        audio = model.apply_tts(text=prepared, speaker=speaker, sample_rate=SAMPLE_RATE,
                                put_accent=True, put_yo=True)
    pcm = (audio.clamp(-1, 1) * 32767).to(torch.int16).numpy().tobytes()
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SAMPLE_RATE)
        w.writeframes(pcm)
    return buf.getvalue(), len(pcm) / 2 / SAMPLE_RATE


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/health":
            self._send(200, b"ok", "text/plain")
        elif self.path == "/speakers":
            self._send(200, json.dumps(model.speakers).encode(), "application/json")
        else:
            self._send(404, b"not found", "text/plain")

    def do_POST(self):
        if self.path != "/tts":
            return self._send(404, b"not found", "text/plain")
        try:
            request = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))))
            begin = time.perf_counter()
            wav, seconds = synthesize(str(request["text"]), str(request.get("speaker", DEFAULT_SPEAKER)))
            ms = (time.perf_counter() - begin) * 1000
        except Exception as e:  # кривой запрос, неизвестный голос или модель отказалась (слишком длинный текст)
            return self._send(400, f"{type(e).__name__}: {e}".encode(), "text/plain")
        self._send(200, wav, "audio/wav", {"X-Synth-Ms": f"{ms:.0f}", "X-Audio-Seconds": f"{seconds:.2f}"})

    def _send(self, code, body, content_type, headers=None):
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        for key, value in (headers or {}).items():
            self.send_header(key, value)
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass  # без строки в логе на каждый запрос


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8000), Handler).serve_forever()
