### Usage (Docker deployment)
copy-paste on your server
```bash
git pull && mv env.example .env
```
And add your discord token bot in .env, then:
```bash
docker compose up -d && docker compose logs -f -t
```

Бот довольно простой и наверно не очень пригоден для больших серверов. 
Из минусов: бот не закрепляется за каналом, из вашего канала его может "украсть" обычным джоином любой другой пользователь сервера. Но бот спокойно работает на множестве серверов. Возможны ошибки
