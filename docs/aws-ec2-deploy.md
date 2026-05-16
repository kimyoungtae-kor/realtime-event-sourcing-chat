# AWS EC2 Temporary Deploy

면접 결과가 나올 때까지만 공개 URL로 확인할 수 있게 두는 임시 배포 절차다.

이 방식은 빠른 확인을 우선한다.

- EC2 한 대에서 Spring Boot app과 MySQL을 Docker Compose로 실행한다.
- MySQL은 EC2 외부로 포트를 열지 않는다.
- 운영 전환 시에는 MySQL을 RDS로 분리하고 app은 ECS/Fargate 또는 EKS로 옮긴다.

## 준비 파일

- `Dockerfile`
- `docker-compose.aws.yml`
- `deploy/aws.env.example`

## EC2 권장값

임시 확인용:

- Ubuntu 22.04 또는 24.04 LTS
- `t3.small` 이상 권장
- Storage 20GB 이상

메모리가 빠듯하면 `JAVA_OPTS=-Xms128m -Xmx384m`로 줄일 수 있다.

## Security Group

Inbound:

| Port | Source | Purpose |
| ---: | --- | --- |
| 22 | 내 IP | SSH |
| 8080 | 면접관 확인이 필요하면 `0.0.0.0/0`, 더 안전하게는 내 IP/면접관 IP | API/WebSocket |

MySQL `3306`은 열지 않는다.

## EC2 접속 후 설치

Ubuntu 기준:

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl git
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker $USER
```

설치 후 SSH를 다시 접속한다.

## 배포

```bash
git clone https://github.com/kimyoungtae-kor/realtime-event-sourcing-chat.git
cd realtime-event-sourcing-chat
cp deploy/aws.env.example .env.aws
vi .env.aws
```

반드시 바꿀 값:

- `MYSQL_ROOT_PASSWORD`
- `MYSQL_PASSWORD`
- `DB_PASSWORD`

`MYSQL_PASSWORD`와 `DB_PASSWORD`는 같은 값으로 맞춘다.

실행:

```bash
docker compose --env-file .env.aws -f docker-compose.aws.yml up -d --build
```

확인:

```bash
docker compose --env-file .env.aws -f docker-compose.aws.yml ps
curl http://localhost:8080/actuator/health
```

브라우저/Postman에서는:

```text
http://{EC2_PUBLIC_IP}:8080/actuator/health
http://{EC2_PUBLIC_IP}:8080/ws-test.html
```

## 로그 확인

```bash
docker logs -f resc-api
docker logs -f resc-mysql
```

## 업데이트

```bash
git pull
docker compose --env-file .env.aws -f docker-compose.aws.yml up -d --build
```

## 종료와 비용 정리

면접 확인이 끝나면 비용 방지를 위해 아래 중 하나를 수행한다.

완전 삭제:

```bash
docker compose --env-file .env.aws -f docker-compose.aws.yml down -v
```

EC2도 중지 또는 종료한다.

## 운영 전환 시 변경점

임시 배포에서는 MySQL도 같은 EC2에 둔다. 운영 전환 시에는 아래 구조가 더 적합하다.

- App: ECS/Fargate, EKS, 또는 Elastic Beanstalk
- DB: Amazon RDS for MySQL
- Secrets: AWS Secrets Manager 또는 SSM Parameter Store
- Logs/Metrics: CloudWatch
- WebSocket ingress: ALB
