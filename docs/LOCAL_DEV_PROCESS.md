# 로컬 개발 환경 가이드

## 개발 방식
- **인프라** (DB, Redis, S3 등): Docker Compose로 실행
- **앱**: IntelliJ에서 Spring Boot 직접 실행

---

## 최초 세팅 (1회)

1. 프로젝트 루트에 `.env` 파일 생성 및 환경변수 작성
2. `init-s3.sh` 줄바꿈 형식 변경: `CRLF` → `LF` (IntelliJ 우측 하단)
3. 실행 권한 부여:
```bash
chmod +x init-s3.sh
```

---

## 실행 순서

**Step 1. 인프라 실행 (Terminal)**
```bash
docker compose down -v --remove-orphans
docker compose up -d
```

**Step 2. 앱 실행 (IntelliJ)**
- `Aibe4FinalProjectTeam4Application` 실행

---

## S3 환경 스위칭

### LocalStack (기본값)
`application-dev.yml`
```yaml
spring.cloud.aws.s3:
  endpoint: http://localhost:4566
  path-style-access-enabled: true
```

### 실제 AWS S3
`application-dev.yml` → 위 두 줄 주석 처리 후 앱 재시작

---

## LocalStack CLI

**초기 프로필 설정 (1회)**
```bash
aws configure --profile localstack
# Access Key: test / Secret Key: test / Region: ap-northeast-2 / Format: json
```

**주요 명령어**
```bash
# 전체 버킷 목록 확인
aws --endpoint-url=http://localhost:4566 s3 ls --profile localstack

# 특정 버킷 내 파일 리스트 확인
aws --endpoint-url=http://localhost:4566 s3 ls s3://team4-documind-bucket/ --profile localstack

# 폴더 구조까지 상세 확인
aws --endpoint-url=http://localhost:4566 s3 ls s3://team4-documind-bucket/ --recursive --profile localstack

# 로컬 파일을 S3로 업로드
aws --endpoint-url=http://localhost:4566 s3 cp test.pdf s3://team4-documind-bucket/ --profile localstack

# S3 파일을 로컬로 다운로드
aws --endpoint-url=http://localhost:4566 s3 cp s3://team4-documind-bucket/test.pdf ./ --profile localstack

# 특정 파일 삭제
aws --endpoint-url=http://localhost:4566 s3 rm s3://team4-documind-bucket/test.pdf --profile localstack

# 파일 경로(이름) 변경
aws --endpoint-url=http://localhost:4566 s3 mv s3://team4-documind-bucket/old.pdf s3://team4-documind-bucket/new.pdf --profile localstack
```

**`awslocal` 사용 시 옵션 생략 가능**
```bash
pip install awscli-local
awslocal s3 ls s3://team4-documind-bucket/
```
