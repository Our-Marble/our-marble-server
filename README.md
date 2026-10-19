# 우리의 마블 서버 (our-marble-server)

"모두의 마블"을 모티브로 한 **2~4인 실시간 온라인 보드게임**의 Java/Spring Boot 게임 서버입니다.
Unity 클라이언트와 WebSocket으로 통신하며, 모든 난수와 게임 판정을 서버가 맡는 **서버 권위(Server Authoritative)** 구조입니다.

## 핵심 특징

- **서버 권위 구조**: 주사위, 통행료, 파산, 순위까지 서버가 판정합니다. 클라이언트는 "유저의 선택"만 보내고, 모든 요청은 서버가 다시 검증합니다.
- **끊김 없는 진행**: 입력 제한 시간이 지나면 서버가 대신 진행합니다. 자리를 비운 플레이어는 AFK로 처리되고, 재접속하면 현재 게임 상태를 복구합니다.
- **봇 플레이**: 방장이 봇 수를 정하면 봇이 사람과 같은 규칙과 같은 서비스 함수로 진행합니다.
- **수평 확장**: 방·게임 상태, 접속 기록, 타이머를 Redis에 두어 서버를 여러 대 띄워도 같은 게임을 봅니다.
- **STOMP 없는 WebSocket**: 연결 하나로 로비부터 게임까지 평평한 JSON `{ "type": ..., 필드... }`만 주고받습니다.

## 기술 스택

| 영역 | 기술 |
| --- | --- |
| 언어/프레임워크 | Java 21, Spring Boot 4.1.1 |
| 실시간 통신 | Spring WebSocket (직접 구현), Jackson 3 |
| 저장소 | Redis 7.4 (방·게임 상태, 타이머, 접속 기록, Pub/Sub, 분산 락), MySQL 8.4 (회원·전적) |
| 인증 | Spring Security, JWT (jjwt) |
| 메일 | Spring Mail, Mailpit (개발용) |
| 문서 | springdoc-openapi, Redoc |
| 테스트 | JUnit 5, AssertJ, Mockito |
| 인프라 | AWS EC2, Docker Compose, GitHub Actions |

## 아키텍처

클라이언트는 HTTP로 로그인해 JWT를 받고, 그 토큰으로 WebSocket 연결 하나를 열어 끝까지 사용합니다.

```
클라이언트  ──{type, ...}──▶  MainWebSocketHandler   연결·세션 관리, 메시지 해석(MessageCodec)
                                    │
                              MessageRouter           type으로 핸들러 선택
                                    │
                  ┌─────────────────┴─────────────────┐
          LobbyMessageHandler                  GameMessageHandler
                  │                                   │
            LobbyService                        GameService  ← 규칙 처리, GameResult 반환
                  │                                   │
                  └──────────────┬────────────────────┘
                         WebSocketMessageSender        단일 전송 출구
                                 │
                  한 명: PlayerUnicaster
                  방 전체: Redis Pub/Sub → 각 서버의 RoomBroadcaster
```

- 서비스는 `WebSocketSession`을 모릅니다. 알림을 직접 보내지 않고 `GameResult`로 돌려주면 핸들러가 전송합니다.
- 서비스 요청 함수는 `roomId` 없이 `playerId`만 받습니다. 접속 시 JWT로 확인한 값이라 위조할 수 없습니다.
- 요청 처리 함수는 `synchronized`로 한 번에 하나씩 처리하고, 서버 사이에서는 Redis 타이머의 리스가 한 방의 요청을 한 곳에서만 처리하게 합니다.

| 데이터 | 저장 위치 | 이유 |
| --- | --- | --- |
| 방/게임 상태 | Redis | 매 요청 읽고 쓰기, 서버 간 공유 |
| 접속 기록 | Redis (TTL 60초) | 서버가 죽으면 자동 정리 |
| 제한 시간 타이머 | Redis | 어느 서버든 마감을 보고 대신 진행 |
| 회원/전적 | MySQL | 오래 남아야 하는 데이터 |
| WebSocket 연결 | 각 서버 메모리 | 실제 소켓이라 옮길 수 없음 |

## 패키지 구조

```
com.sparta.ourmarbleserver
├─ game/       게임 진행 (GameService, ArrivalService, PropertyTradeService, CardService, TurnService, MoveService ...)
├─ property/   땅 데이터와 금액 계산 (통행료, 투자금, 매각가, 인수가)
├─ economy/    현금 이동 (월급, 세금, 적립금, 이체)
├─ card/       황금열쇠 DTO
├─ bot/        봇 판단 (IBotStrategy, BasicBotStrategy, BotSettings)
├─ scheduler/  제한 시간 타이머와 대신 진행 (TimerService, AutoPlayService)
├─ lobby/      로비·대기방
├─ auth/       회원가입, 이메일 인증, JWT 로그인, 전적
├─ config/     보안 설정, Redoc 문서 설정
└─ global/     WebSocket 전송 계층, 예외, 프로토콜
```

## 게임 규칙 요약

- 32칸 보드, 2~4인. 땅 23개를 사고 건물(별장 → 빌딩 → 호텔)을 한 단계씩 올릴 수 있습니다.
- 남의 땅에 도착하면 통행료를 냅니다. 현금이 모자라면 땅을 팔고, 전부 팔아도 모자라면 파산합니다.
- 현금이 충분하면 통행료를 낸 뒤 상대의 땅을 투자금의 200%로 인수할 수 있습니다. 매각가는 투자금의 50%입니다.
- 출발 지점을 지나면 월급, 세무조사 칸에서는 세금을 내서 적립금이 쌓이고, 적립금 칸에서는 쌓인 금액을 전부 받습니다.
- 세계여행, 건설 불가 땅(관광지), 황금열쇠, 무인도(더블이 나오거나 영업정지가 끝나야 탈출), 3연속 더블 시 무인도 이동을 지원합니다.
- 종료 조건: 생존자 1명 이하 또는 최대 라운드 도달. 순위는 총자산 → 현금 → 턴 순서로 정합니다.
- 초기 자금, 월급, 세금, 최대 라운드, 무인도 영업정지 턴, 입력 제한 시간은 방장이 대기실에서 방별로 정합니다(`GameConfig`).

## 서버 권위와 검증

모든 게임 요청은 아래 순서로 검증하고, 검증을 모두 끝낸 뒤에만 상태를 바꿉니다. 실패하면 상태는 그대로입니다.

1. 플레이어가 속한 진행 중인 게임이 있는가
2. 요청한 땅이 존재하는가
3. 내 차례인가, 파산하지 않았는가
4. 요청이 현재 단계(phase)에 맞는가
5. 입력 제한 시간 안인가, 다른 요청을 처리 중이 아닌가
6. 기능별 검증 (내 말이 그 땅 위에 있는가, 현금, 소유자, 건물 단계 등)

금액 계산은 정수 연산이며, 알림에는 금액을 싣지 않습니다. 클라이언트가 같은 규칙으로 계산합니다.

## 제한 시간, AFK, 봇

- 요청을 기다리는 단계마다 "다음 요청 수신 마감시간"을 Redis에 기록합니다. 사람은 방 설정의 제한 시간(기본 60초), 봇은 짧은 대기(6초) 뒤에 진행합니다.
- 마감이 지나면 마감이 지난 방을 찾은 서버 한 대가 `AutoPlayService`로 대신 진행하고, 사람은 AFK로 표시됩니다. AFK인 사람은 다음 입력부터 짧게 기다린 뒤 대신 진행하며, 요청을 보내거나 재접속하면 AFK가 풀립니다.
- 봇과 AFK 대신 진행은 사람의 요청과 **같은 서비스 함수**를 호출하므로 검증 규칙이 똑같이 적용됩니다.
- 봇 판단은 통행료 위험, 수익 비율, 회수 기간, 기회비용을 확률로 계산합니다. 성향별 기준값은 `BotSettings`로 분리돼 있습니다.

## WebSocket 메시지

모든 메시지는 `{ "type": "...", ...필드 }` 형태입니다.

**게임 요청 (클라이언트 → 서버)**

| type | 본문 | 설명 |
| --- | --- | --- |
| `ROLL_DICE` | - | 주사위 굴리기 |
| `PURCHASE_PROPERTY` | `propertyId`, `isAccept` | 땅 구매·거절 |
| `BUILD` | `propertyId`, `isAccept` | 건설·거절 |
| `ACQUIRE_PROPERTY` | `propertyId`, `isAccept` | 인수·거절 |
| `SELL_PROPERTIES` | `propertyIds` | 매각 |
| `CHOOSE_DESTINATION` | `destinationPosition` | 세계여행 목적지 선택 |
| `DRAW_CARD` | - | 황금열쇠 뽑기 |
| `GAME_STATE_REQUEST` | - | 재접속 시 상태 요청 |

**게임 알림 (서버 → 클라이언트)**: `DICE_ROLLED`, `DESTINATION_CHOSEN`, `CARD_DRAWN`, `PROPERTY_PURCHASED`, `BUILT`, `PROPERTY_ACQUIRED`, `PROPERTIES_SOLD`, `GAME_STATE_SYNC`

**로비**: `CREATE_ROOM`, `ENTER_ROOM`, `LEAVE_ROOM`, `SET_READY`, `START_GAME`, `ROOM_LIST_REQUEST`, `ROOM_CONFIG_REQUEST`, `CHAT_SEND` 등 (알림: `ROOM_UPDATED`, `GAME_STARTED`, `ROOM_LIST`, `PLAYER_RECORD`, `CHAT_BROADCAST` 등)

**오류**: `COMMON_ERROR`, `LOBBY_ERROR`, `GAME_ERROR` 3종이며, 클라이언트는 `code`로 분기합니다.

요청·알림의 필드와 예시, 오류 코드 표는 서버 실행 후 `/docs.html`(Redoc)에서 확인할 수 있습니다.

## 배포

- AWS EC2에 서버를 여러 대 띄우고 로드밸런서로 분산합니다. 상태가 Redis에 있어서 어느 서버에 붙어도 같은 방과 게임을 봅니다.
- GitHub Actions(`deploy.yml`)로 배포합니다.
- 정상 종료 시 WebSocket 연결을 먼저 닫아 클라이언트가 정상 종료 코드를 받도록 했습니다.
- 서버 간 중복 로그인은 접속 기록으로 감지해 옛 연결을 끊습니다.

## 팀

| 이름 | 담당 |
| --- | --- |
| 김건욱 | 클라이언트 코어·네트워크, 배포 |
| 한수정 | 서버 네트워크·로비, Redis·다중 서버 |
| 이근희 | 서버 게임 로직, 봇, 타이머 |
| 김현규 | 클라이언트 UI·연출, 대기방 채팅 |
| 김희정 | 서버 인증·전적, 보드·캐릭터 비주얼 |