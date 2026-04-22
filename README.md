# MobileLLMServer

Run local LLMs on your phone and serve them to your IDE.

## What this project does

MobileLLMServer runs MediaPipe-compatible local LLMs (for example Gemma variants) on-device and exposes an OpenAI-compatible API over your local network.

## OpenAI-compatible APIs

- `GET /v1/models`
- `POST /v1/chat/completions`

Default base URL:

- `http://[phone-ip]:8080/v1`

## VS Code setup

Use your phone's local IP address and configure your extension provider endpoint to:

- `http://[phone-ip]:8080/v1`

Examples:

- Continue.dev: set OpenAI-compatible base URL to `http://[phone-ip]:8080/v1`
- Llama Coder or similar tools: set OpenAI API host/base URL to `http://[phone-ip]:8080/v1`

Make sure your IDE machine and phone are on the same local network.

## Models

- Built-in model management/download remains available in the app.
- Imported local model metadata is exposed via `/v1/models`.

### Hugging Face models (`.bin`)

The app now accepts MediaPipe-compatible `.bin` model files in validation paths and includes a prepared UI path for Hugging Face URL-based imports.

When adding models manually, ensure they are MediaPipe-compatible (`.task`, `.litertlm`, or `.bin`).

## Development

Android project location:

- `/home/runner/work/MobileLLMServer/MobileLLMServer/Android/src`

See:

- `DEVELOPMENT.md`
