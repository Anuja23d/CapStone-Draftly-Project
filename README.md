## Draftly – Gmail AI Reply Agent (Capstone)

Backend + Chrome extension that:
- Fetches recent **Inbox / Unread** emails from Gmail via OAuth2 + Gmail API
- Learns tone from your Sent emails
- Generates AI drafts
- Lets you review/edit/approve/reject drafts
- Sends approved replies via Gmail API with **proper threading** (`threadId`, `In-Reply-To`, `References`)
- Stores drafts + status history logs in MySQL

### Tech stack
- **Spring Boot 3.3.x** (REST APIs)
- **Spring Security OAuth2 Client** (Google login)
- **MySQL + Spring Data JPA**
- **Spring AI ChatClient** (OpenAI-compatible endpoint via OpenRouter)
- **Chrome extension (Manifest v3)** for in-Gmail UX

---

## Setup

### 1) Backend prerequisites
- Java 21
- Maven
- MySQL running locally

Update DB config in `draftly/src/main/resources/application.yml`:
- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`

### 2) Google OAuth setup
Configure your Google OAuth Client (Web application) to include:
- **Authorized redirect URI**: `http://localhost:8080/login/oauth2/code/google`

Start backend and login once:
- Open: `http://localhost:8080/oauth2/authorization/google`
- On success: `/auth/success` stores tokens in DB

### 3) LLM provider setup (OpenRouter)
This project uses an OpenRouter key (`sk-or-...`) with an OpenAI-compatible endpoint.
Config is in `draftly/src/main/resources/application.yml`:
- `spring.ai.openai.base-url: https://openrouter.ai/api`
- `spring.ai.openai.api-key: <your-key>`
- `spring.ai.openai.chat.options.model: openai/gpt-4o-mini`

---

## API Reference (Backend)

### Emails
- **Fetch Inbox / Unread**
  - `GET /emails/inbox?email=<your@gmail.com>&maxResults=10&unreadOnly=true`
  - Persists records in `Email` (includes `messageId`, `threadId`, `subject`, `sender`, `snippet`, `body`)

### Draft lifecycle
- **Generate from raw body**
  - `POST /drafts/draft/generate`
  - Body: `{ "email": "<user@gmail.com>", "body": "<email text>" }`

- **Generate for an Email row**
  - `POST /drafts/draft/generate-for-email`
  - Body: `{ "email": "<user@gmail.com>", "emailId": 123 }`

- **List drafts**
  - `GET /drafts`
  - `GET /drafts/status/PENDING`

- **Edit draft**
  - `PATCH /drafts/{draftId}`
  - Body: `{ "editedText": "..." }`

- **Approve / Reject**
  - `POST /drafts/{draftId}/approve`
  - `POST /drafts/{draftId}/reject`

### Send reply (thread-safe)
Two options:

1) **Send an approved draft** (recommended)
- `POST /drafts/{draftId}/send`
- Body: `{ "email": "<user@gmail.com>", "idempotencyKey": "<uuid>" }`
- Retries up to 3 times and stores logs.

2) **Send a direct reply** (low-level)
- `POST /emails/reply/send`
- Body: `{ "email": "<user@gmail.com>", "messageId": "<gmailMessageId>", "replyText": "..." }`

### Logs
- `GET /logs/draft/{draftId}`

### User signature (appended at end of draft)
- `POST /users/signature`
  - Body: `{ "email":"<user@gmail.com>", "signature":"Best regards\\nName\\nPhone\\nEmail" }`
- `GET /users/signature?email=<user@gmail.com>`

### Preferences (tone)
- `POST /preferences`
  - Body: `{ "email":"<user@gmail.com>", "tone":"FORMAL" }`
  - Allowed: `DEFAULT`, `FORMAL`, `FRIENDLY`, `CONCISE`
- `GET /preferences?email=<user@gmail.com>`

---

## Chrome extension
1. Chrome → `chrome://extensions`
2. Enable **Developer mode**
3. **Load unpacked** → select `draftly-extension/`
4. Open Gmail → open an Inbox email → click **Reply**
5. Click **Generate Draft**

Note: the extension stores your Draftly Gmail email in Chrome local storage the first time it runs.

---

## Design notes (why this satisfies the case study)
- **Inbox fetch**: `GET /emails/inbox` loads unread/recent messages with metadata.
- **Tone learning**: pulls Sent emails (`in:sent`) and builds tone context.
- **Human-in-the-loop**: drafts are persisted with statuses; approve/reject/send endpoints exist.
- **Thread integrity**: backend sends using `threadId` + `In-Reply-To` + `References`.
- **Idempotency**: send endpoint requires `idempotencyKey` to prevent duplicate sends.
- **Retries**: send endpoint retries transient failures and logs attempts.

