# Evaluation

## Offline baseline

Run the default test suite with:

```powershell
mvn -q test
```

Surefire excludes tests tagged `external-api` and `mysql` by default. The
offline suite does not require MySQL or external API credentials.

The repository includes 30 deterministic regression cases covering tool
validation, interview state transitions, RAG filtering and fallback behavior,
security boundaries, concurrency contracts, and metrics. The latest local
baseline passed all 30 cases.

## Database integration

Run MySQL tests separately after initializing a local database and setting
`DB_USERNAME` and `DB_PASSWORD`:

```powershell
mvn -q -Pmysql-tests test
```

These tests validate real SQL mappings and request/lease concurrency. They are
not included in the offline baseline.

## External API evaluation

External model, embedding, and reranking tests are tagged `external-api` and
are intentionally excluded from normal builds. They require credentials,
network access, and a locally generated knowledge-base index. They should be
run only in a controlled environment:

```powershell
mvn -q -Pexternal-api-tests test
```

The Java and AI RAG gold datasets under
`src/test/resources/evaluation/` are independent of the 30-case offline
regression set. Their scores describe retrieval on those fixed datasets and
do not represent end-to-end answer quality.
