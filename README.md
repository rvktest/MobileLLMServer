# MobileLLMServer

Run local LLMs on your phone and serve them to your IDE.

## What this project does

MobileLLMServer runs MediaPipe-compatible local LLMs (for example Gemma variants) on-device and exposes an OpenAI-compatible API over your local network.

## OpenAI-compatible APIs

- `GET /healthz`
- `GET /readyz`
- `GET /v1/models`
- `POST /v1/chat/completions`

Default base URL:

- `http://[phone-ip]:8080/v1`

Current behavior in this first test version:

- chat completions run on the model currently selected and initialized in the app UI;
- only one active inference request is supported at a time;
- `GET /readyz` reports whether the server is ready, still initializing a model, or busy;
- the server currently uses the last `user` message content as the prompt;
- the selected model is persisted and restored after model loading;
- server autostart intent is persisted so the app can restore the server state path in later sessions;
- request-level model switching is not supported yet.

Readiness states currently returned by `GET /readyz`:

- `ready`
- `busy`
- `model_initializing`
- `no_model_selected`
- `server_stopped`

## VS Code setup

Use your phone's local IP address and configure your extension provider endpoint to:

- `http://[phone-ip]:8080/v1`

Examples:

- Continue.dev: set OpenAI-compatible base URL to `http://[phone-ip]:8080/v1`
- Llama Coder or similar tools: set OpenAI API host/base URL to `http://[phone-ip]:8080/v1`

Note: the official GitHub Copilot extension is not a drop-in OpenAI-compatible custom endpoint client. For this project, OpenAI-compatible tools such as Continue and similar clients are the realistic first targets.

Make sure your IDE machine and phone are on the same local network.

## Security note

The API is intended for trusted local-network use. Keep your phone on a trusted LAN when the server is running.

## Models

- Built-in model management/download remains available in the app.
- Imported local model metadata is exposed via `/v1/models`.

### Hugging Face models (`.bin`)

The app now accepts MediaPipe-compatible `.bin` model files in validation paths.

The UI also contains a placeholder path for Hugging Face URL-based imports, but that flow is not implemented yet.

When adding models manually, ensure they are MediaPipe-compatible (`.task`, `.litertlm`, or `.bin`).

## Development

Android project root:

- `Android/src`

See:

- `DEVELOPMENT.md`
