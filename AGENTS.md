# NimoKids — agent instructions (backend)

Before any task, read `../../04-Documentation/docs_updated_en/AI_CONTEXT_INDEX.md` (relative to this repo) and follow its procedure:
always-read core → classify the task → read only the files its routing table lists → verify "Known drift" items in code.

Quick rules: the backend decides correctness, score, streak and timeout (the UI only renders); real API contract is
`03-technical/API_CONTRACT.md`; never print secrets; commit/push only when asked.

Note: the docs folder is not inside this git repo, so it is only available on the developer machine.
