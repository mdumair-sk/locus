# Catalog Model Proposal

## ⚠️ Branch Protection & Scope Enforcement (NF-8)
> **CRITICAL RULE**: Catalog pull requests (whether opened by the discovery bot or by a trusted contributor)
> **MUST NOT** modify any files outside the `catalog/` directory.
>
> The CI workflow (`.github/workflows/catalog-discovery.yml`) automatically asserts this via
> `git diff --name-only` restricted to `catalog/` and **will fail the job** if any file outside `catalog/`
> is touched.

---

## 📋 Human Reviewer Checklist
Before merging any candidate model into `catalog/models.json`, a human maintainer must complete the following verifications:

- [ ] **License Compatibility**: Confirm the model's license (from Hugging Face model card / tags) is open and permissive (e.g., Apache-2.0, MIT, Gemma, etc.) and allows mobile redistribution/usage without legal risk.
- [ ] **Quant Availability & Mobile Feasibility**: Confirm the specified GGUF quantization (preferring `Q4_K_M` or `Q8_0`) exists in the repository, is a single file (not an unsupported multi-part shard), and file size is feasible for mobile devices (typically ≤ 8 GB).
- [ ] **Checksum Re-Verification**: Confirm that the recorded `sha256` in `catalog/models.json` matches the Git LFS OID from the Hugging Face tree API endpoint:
  ```bash
  curl -sL https://huggingface.co/api/models/<REPO_ID>/tree/main | grep -B 2 -A 5 "<FILENAME>"
  ```
- [ ] **Task Category Assignment**: Assign the model from `"candidates"` into the appropriate production category (`"chat"`, `"utility"`, or `"embeddings"`), or update its `suggestedCategory`, and ensure `id`, `name`, `contextLength`, and `description` are correctly formatted.
- [ ] **Strict Scope Check**: Verify that `git diff --name-only` confirms only `catalog/models.json` has been modified.

---

## Candidate Model Summary
<!-- The discovery bot or contributor should list the candidate models below -->
