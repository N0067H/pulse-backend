# Pulse Backend

> API health monitoring service — register endpoints, get alerted when they go down.

**[한국어](#한국어) | [English](#english)**

> Frontend repo: [pulse-frontend](https://github.com/n0067h/pulse-frontend)

---

## English

### Overview

Pulse Backend is a Spring Boot (WebFlux) service that periodically sends HTTP requests to registered API endpoints and records the results. When consecutive failures exceed a configured threshold, it publishes an alert via AWS SNS.

### Architecture

```
pulse-frontend  (SvelteKit)  →  pulse-backend  (Spring Boot + WebFlux)
                                      ↓                    ↓
                                  DynamoDB              AWS SNS
                               apis / check_results    (alert email)
```

---

**Key features**

- Register any HTTP endpoint to monitor (GET, POST, etc.)
- Configurable check interval, timeout, and retry count per API
- Results stored in DynamoDB with a 30-day TTL
- Alert via AWS SNS email when consecutive failures reach the threshold
- Alert cooldown to prevent notification flooding
- Enable/disable monitoring per API without deleting it

**Tech stack**

| Layer | Technology |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 4.0.5 (WebFlux) |
| Database | AWS DynamoDB |
| Alerting | AWS SNS |
| Build | Gradle |

---

### Prerequisites

- Java 21
- AWS account with DynamoDB and SNS configured
- AWS credentials available locally (see below)

---

### AWS Setup

#### 1. DynamoDB tables

Create the following two tables:

**`apis`**
| Attribute | Type | Key |
|---|---|---|
| `api_id` | String | Partition key |

**`check_results`**
| Attribute | Type | Key |
|---|---|---|
| `api_id` | String | Partition key |
| `checked_at` | String | Sort key |

Enable TTL on `check_results` using the `ttl` attribute.

#### 2. SNS topic

Create an SNS topic and subscribe an email address to it. Copy the topic ARN — you will need it as an environment variable.

#### 3. IAM permissions

The AWS principal running this service needs the following permissions:

```json
{
  "Effect": "Allow",
  "Action": [
    "dynamodb:GetItem",
    "dynamodb:PutItem",
    "dynamodb:UpdateItem",
    "dynamodb:DeleteItem",
    "dynamodb:Scan",
    "dynamodb:Query",
    "sns:Publish"
  ],
  "Resource": [
    "arn:aws:dynamodb:<region>:<account-id>:table/apis",
    "arn:aws:dynamodb:<region>:<account-id>:table/check_results",
    "arn:aws:sns:<region>:<account-id>:<topic-name>"
  ]
}
```

---

### Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `AWS_REGION` | No | `us-east-1` | AWS region where DynamoDB and SNS are provisioned |
| `AWS_SNS_TOPIC_ARN` | **Yes** | — | ARN of the SNS topic for alert emails |
| `AWS_ACCESS_KEY_ID` | No* | — | AWS access key (if not using instance role or `~/.aws/credentials`) |
| `AWS_SECRET_ACCESS_KEY` | No* | — | AWS secret key |

\* AWS SDK resolves credentials via the [default credential provider chain](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html). Environment variables are one option; IAM instance roles or `~/.aws/credentials` also work.

---

### Running

```bash
# Set required environment variables
export AWS_SNS_TOPIC_ARN=arn:aws:sns:us-east-1:123456789012:pulse-alert
export AWS_REGION=us-east-1

# Run
./gradlew bootRun
```

The server starts on **port 8080**.

---

### API Reference

#### Register an API to monitor

```
POST /apis
```

```json
{
  "url": "https://example.com/health",
  "method": "GET",
  "interval_seconds": 180,
  "timeout_seconds": 5,
  "retry_count": 1,
  "alert_threshold": 3,
  "alert_cooldown_seconds": 3600
}
```

| Field | Type | Constraints | Description |
|---|---|---|---|
| `url` | string | required, valid URL | Endpoint to monitor |
| `method` | string | required | HTTP method (`GET`, `POST`, …) |
| `interval_seconds` | int | min 1 | How often to check (seconds) |
| `timeout_seconds` | int | min 1 | Request timeout (seconds) |
| `retry_count` | int | 0–2 | Retries on timeout or connection error |
| `alert_threshold` | int | min 1 | Consecutive failures before alerting |
| `alert_cooldown_seconds` | int | min 0 | Minimum seconds between repeated alerts |

#### Other endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/apis` | List all registered APIs |
| `GET` | `/apis/{apiId}` | Get a single API |
| `DELETE` | `/apis/{apiId}` | Delete an API |
| `PATCH` | `/apis/{apiId}/enabled` | Toggle monitoring on/off |
| `GET` | `/apis/{apiId}/results?limit=50` | Get check history (newest first) |

---

---

## 한국어

### 개요

Pulse Backend는 등록된 API 엔드포인트에 주기적으로 HTTP 요청을 보내고 결과를 기록하는 Spring Boot(WebFlux) 서비스입니다. 연속 실패 횟수가 설정한 임계값을 초과하면 AWS SNS를 통해 알림 이메일을 발송합니다.

### 아키텍처

```
pulse-frontend  (SvelteKit)  →  pulse-backend  (Spring Boot + WebFlux)
                                      ↓                    ↓
                                  DynamoDB              AWS SNS
                               apis / check_results    (알림 이메일)
```

---

**주요 기능**

- HTTP 엔드포인트 등록 및 모니터링 (GET, POST 등)
- API별 점검 주기, 타임아웃, 재시도 횟수 설정
- 점검 결과를 DynamoDB에 저장 (TTL 30일 자동 삭제)
- 연속 실패 횟수 초과 시 AWS SNS 이메일 알림
- 알림 쿨다운으로 중복 발송 방지
- API별 모니터링 활성화/비활성화 (삭제 없이)

**기술 스택**

| 영역 | 기술 |
|---|---|
| 런타임 | Java 21 |
| 프레임워크 | Spring Boot 4.0.5 (WebFlux) |
| 데이터베이스 | AWS DynamoDB |
| 알림 | AWS SNS |
| 빌드 | Gradle |

---

### 사전 준비

- Java 21
- DynamoDB와 SNS가 설정된 AWS 계정
- 로컬에서 사용할 AWS 자격 증명

---

### AWS 설정

#### 1. DynamoDB 테이블 생성

아래 두 테이블을 생성합니다.

**`apis`**
| 속성 | 타입 | 키 |
|---|---|---|
| `api_id` | String | 파티션 키 |

**`check_results`**
| 속성 | 타입 | 키 |
|---|---|---|
| `api_id` | String | 파티션 키 |
| `checked_at` | String | 정렬 키 |

`check_results` 테이블에서 `ttl` 속성으로 TTL을 활성화합니다.

#### 2. SNS 토픽 생성

SNS 토픽을 생성하고 이메일 주소를 구독(Subscribe)합니다. 이후 환경 변수로 사용할 토픽 ARN을 복사해 둡니다.

#### 3. IAM 권한

서비스를 실행하는 AWS 주체(IAM 사용자 또는 역할)에 아래 권한이 필요합니다.

```json
{
  "Effect": "Allow",
  "Action": [
    "dynamodb:GetItem",
    "dynamodb:PutItem",
    "dynamodb:UpdateItem",
    "dynamodb:DeleteItem",
    "dynamodb:Scan",
    "dynamodb:Query",
    "sns:Publish"
  ],
  "Resource": [
    "arn:aws:dynamodb:<region>:<account-id>:table/apis",
    "arn:aws:dynamodb:<region>:<account-id>:table/check_results",
    "arn:aws:sns:<region>:<account-id>:<topic-name>"
  ]
}
```

---

### 환경 변수

| 변수 | 필수 | 기본값 | 설명 |
|---|---|---|---|
| `AWS_REGION` | 아니오 | `us-east-1` | DynamoDB와 SNS가 위치한 AWS 리전 |
| `AWS_SNS_TOPIC_ARN` | **예** | — | 알림 이메일을 발송할 SNS 토픽 ARN |
| `AWS_ACCESS_KEY_ID` | 아니오* | — | AWS 액세스 키 (인스턴스 역할이나 `~/.aws/credentials`를 사용하지 않는 경우) |
| `AWS_SECRET_ACCESS_KEY` | 아니오* | — | AWS 시크릿 키 |

\* AWS SDK는 [기본 자격 증명 공급자 체인](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html)으로 인증을 처리합니다. 환경 변수 외에도 IAM 인스턴스 역할이나 `~/.aws/credentials` 파일을 사용할 수 있습니다.

---

### 실행

```bash
# 필수 환경 변수 설정
export AWS_SNS_TOPIC_ARN=arn:aws:sns:ap-northeast-2:123456789012:pulse-alert
export AWS_REGION=ap-northeast-2

# 실행
./gradlew bootRun
```

서버는 **8080 포트**에서 시작됩니다.

---

### API 명세

#### API 모니터링 등록

```
POST /apis
```

```json
{
  "url": "https://example.com/health",
  "method": "GET",
  "interval_seconds": 180,
  "timeout_seconds": 5,
  "retry_count": 1,
  "alert_threshold": 3,
  "alert_cooldown_seconds": 3600
}
```

| 필드 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `url` | string | 필수, 유효한 URL | 모니터링할 엔드포인트 |
| `method` | string | 필수 | HTTP 메서드 (`GET`, `POST` 등) |
| `interval_seconds` | int | 최소 1 | 점검 주기 (초) |
| `timeout_seconds` | int | 최소 1 | 요청 타임아웃 (초) |
| `retry_count` | int | 0–2 | 타임아웃/연결 오류 시 재시도 횟수 |
| `alert_threshold` | int | 최소 1 | 알림 발송 기준 연속 실패 횟수 |
| `alert_cooldown_seconds` | int | 최소 0 | 알림 재발송 최소 대기 시간 (초) |

#### 기타 엔드포인트

| 메서드 | 경로 | 설명 |
|---|---|---|
| `GET` | `/apis` | 등록된 전체 API 목록 조회 |
| `GET` | `/apis/{apiId}` | 특정 API 조회 |
| `DELETE` | `/apis/{apiId}` | API 삭제 |
| `PATCH` | `/apis/{apiId}/enabled` | 모니터링 활성화/비활성화 토글 |
| `GET` | `/apis/{apiId}/results?limit=50` | 점검 이력 조회 (최신순) |
