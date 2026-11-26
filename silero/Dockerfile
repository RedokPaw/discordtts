# Silero TTS для бота: русская модель v5_6_ru, CPU.
# Модель — CC BY-NC-SA 4.0 (https://github.com/snakers4/silero-models): только некоммерческое использование.
FROM python:3.12-slim

# CPU-сборка torch: без CUDA образ в разы меньше
RUN pip install --no-cache-dir torch==2.15.0 --index-url https://download.pytorch.org/whl/cpu \
    && pip install --no-cache-dir numpy==2.5.5 num2words==0.5.14

ADD https://models.silero.ai/models/tts/ru/v5_6_ru.pt /models/model.pt

WORKDIR /app
COPY normalize.py server.py test_normalize.py ./
RUN python -m unittest -q test_normalize

# Потоки torch на один синтез; на VPS с 2 ядрами больше 2 смысла нет
ENV TORCH_THREADS=2
# меньше арен glibc: пик памяти под нагрузкой ~870 -> ~650 МиБ без потери скорости
ENV MALLOC_ARENA_MAX=2
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=3)"
CMD ["python", "-u", "server.py"]
