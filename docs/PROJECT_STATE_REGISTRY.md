# Project State Registry

> Registry version: 1
> Last updated: 2026-11-25
> Owner: Fruit Blast Engineering Workflow Orchestrator (`using-agent-skills`)
> Scope: repository/project coordination state only. This registry does not authorize code changes, merges, releases, deployments, or device operations.

## 1. Source of truth and update rules

This file is the repository-level snapshot for task coordination. It complements (and does not replace) approved project instructions, architecture decisions, contracts, supplements, and session handoffs.

Precedence when information conflicts:

1. Live repository and Git history (exact refs and commit SHAs).
2. Current PR, CI, issue, and test evidence.
3. Approved governance documents and ADRs.
4. This registry and the latest session handoff.
5. Conversation history.

Rules:

- Record facts with an exact commit, PR, workflow run, test artifact, or document reference whenever available.
- Distinguish `PROPOSED`, `APPROVED`, `IMPLEMENTED`, `TESTED`, `MERGED`, `DEPLOYED`, and `ACTIVE`. These states are not interchangeable.
- A successful hosted CI run is not proof of physical-device runtime success.
- Do not mark a finding `FIXED` without a change or `VERIFIED` without evidence.
- Do not infer local working-tree cleanliness from GitHub branch state.
- Update this file only within an explicitly authorized scope. A registry entry is not execution approval.
- Keep Android application lifecycle, Accessibility event processing, Activity/window identity, Qwen3-VL integration, native process isolation, Binder lifecycle/reconnect, watchdog recovery, coroutine ownership, single-flight inference, timeout/cancellation, callback invalidation, and resource ownership unchanged unless separately approved.

## 2. Repository baseline

| Field | Current recorded value |
|---|---|
| Repository | `kenznguyen/Smart-Ad-Skip` |
| Default branch | `main` |
| Active P2 branch | `feature/p2-17-autonomous-ai-ad-interaction` |
| Baseline HEAD before O1 | `e7782c4ca94f1cef7b2a551e327017e0e18b8a0e` |
| Baseline commit date | 2026-11-25 05:25:17 UTC |
| Baseline commit subject | `docs(p2.39-p2.43): record integrated behavior and validation` |
| Main HEAD observed during baseline check | `1ddb170b1b5ce02dc77134ddde7dca67e324ffd6` |
| Feature vs main | 438 commits ahead, 0 behind (comparison at baseline) |
| Open pull requests | 0 observed at baseline |
| Local working tree | NOT VERIFIED; remote GitHub inspection cannot establish local uncommitted changes |
| Deployment/runtime state | NOT VERIFIED by this registry |

Repository references:
- [Repository](https://github.com/kenznguyen/Smart-Ad-Skip)
- [Active branch](https://github.com/kenznguyen/Smart-Ad-Skip/tree/feature/p2-17-autonomous-ai-ad-interaction)
- [Branch comparison](https://github.com/kenznguyen/Smart-Ad-Skip/compare/main...feature/p2-17-autonomous-ai-ad-interaction)
- [Pull requests](https://github.com/kenznguyen/Smart-Ad-Skip/pulls)

## 3. Task and milestone state

Status meanings: `IMPLEMENTED` means the change is present on the referenced branch; `CI-PASS` means the cited hosted workflow completed successfully; `DEVICE-PENDING` means physical-device validation has not been established by the cited evidence.

| Task / area | Scope summary | Recorded state | Evidence / remaining condition |
|---|---|---|---|
| P2.17 | Autonomous Qwen3-VL ad action selection with shared Accessibility execution-integrity checks | IMPLEMENTED; CI-PASS; DEVICE-PENDING | [P2.17 design](docs/P2.17-AUTONOMOUS-AI.md); hosted CI does not establish real-device inference/click behavior |
| P2.20 | Native resource safety | IMPLEMENTED on active branch | [P2.20 notes](docs/P2.20-NATIVE-RESOURCE-SAFETY.md) |
| P2.28 | Native process isolation and watchdog recovery | IMPLEMENTED on active branch | [P2.28 notes](docs/P2.28-NATIVE-PROCESS-ISOLATION-WATCHDOG.md) |
| P2.30–P2.34 | IPC timeout ownership, process identity, Binder lifecycle races, deterministic fault injection, watchdog/reconnect race | IMPLEMENTED; instrumentation coverage present | See the corresponding P2.30–P2.34 documents. Hosted CI compiles instrumentation; physical-device execution is a separate gate |
| P2.35 | End-to-end native runtime recovery validation | Instrumentation test implemented; DEVICE-PENDING | [P2.35 notes](docs/P2.35-END-TO-END-NATIVE-RUNTIME-RECOVERY.md) explicitly distinguish the instrumented surrogate from a physical-device run and production native-kernel fault |
| P2.38 | Accessibility window retrieval compatibility | IMPLEMENTED on active branch | [P2.38 notes](docs/P2.38-ACCESSIBILITY-WINDOW-RETRIEVAL-COMPATIBILITY.md) |
| P2.39–P2.43 | EMPTY/FAILED distinction, bounded ad monitoring, CONTINUE, SDK/GAME_HOME recognition, bounded misclick recovery | IMPLEMENTED; regression tests documented; DEVICE-PENDING | [Integration regression](docs/P2.39-P2.43-INTEGRATION-REGRESSION.md); device scenarios remain required |

Do not promote any `DEVICE-PENDING` item to `VERIFIED` without attached device logs or reproducible test evidence.

## 4. CI and verification evidence

Baseline commit: `e7782c4ca94f1cef7b2a551e327017e0e18b8a0e`.

| Workflow | Run ID | Result | What it proves |
|---|---:|---|---|
| Android CI | 45553457476 | completed / success | Hosted Android CI passed for the baseline commit |
| P2.17 Android CI | 45553457477 | completed / success | Unit tests, debug build, and instrumentation-test compilation/upload passed for the baseline commit |

Run links:
- [Android CI run 45553457476](https://github.com/kenznguyen/Smart-Ad-Skip/actions/runs/45553457476)
- [P2.17 Android CI run 45553457477](https://github.com/kenznguyen/Smart-Ad-Skip/actions/runs/45553457477)

Verification boundary:
- The P2.17 workflow runs unit tests, assembles the debug APK, compiles Android instrumentation tests, and uploads artifacts.
- Workflow success does not mean instrumentation tests were executed on a connected device.
- No physical-device runtime PASS is asserted here.
- Re-check CI against the new registry commit before claiming that the new commit is CI-verified.



### O1 post-commit verification

O1 registry commit:
- Commit: `0280ef1b4b61a63e665cbebf96528b868cae30bb`
- Parent: `e7782c4ca94f1cef7b2a551e327017e0e18b8a0e`
- Changed files: exactly `docs/PROJECT_STATE_REGISTRY.md`
- [O1 commit](https://github.com/kenznguyen/Smart-Ad-Skip/commit/0280ef1b4b61a63e665cbebf96528b868cae30bb)

| Workflow | Run ID | Commit tested | Result | Verified coverage |
|---|---:|---|---|---|
| Android CI | 45584844540 | `0280ef1b4b61a63e665cbebf96528b868cae30bb` | completed / success | Unit tests, debug APK assembly, instrumentation-test compilation, artifact upload |
| P2.17 Android CI | 45584844539 | `0280ef1b4b61a63e665cbebf96528b868cae30bb` | completed / success | Unit tests, debug APK assembly, instrumentation-test compilation, artifact upload |

Run links:
- [Android CI run 45584844540](https://github.com/kenznguyen/Smart-Ad-Skip/actions/runs/45584844540)
- [P2.17 Android CI run 45584844539](https://github.com/kenznguyen/Smart-Ad-Skip/actions/runs/45584844539)

Both workflow runs completed successfully on 2026-11-25. Job and step summaries show all listed steps completed with success. These workflows compile instrumentation tests but do not execute them on a physical Android device. This evidence verifies the O1 commit above; it does not pre-verify the follow-up registry-only commit that records this result.

## 5. Findings register

| ID | Severity | Component | Finding / impact | Status | Proposed next action |
|---|---|---|---|---|---|
| ORCH-001 | P1 | Project coordination state | A repository-level source of truth for current project/task state was missing, increasing session drift risk | IMPLEMENTED; commit and hosted CI evidence verified | Continue maintaining the registry only through separately authorized updates |
| ORCH-002 | P1 | Git integration | Active P2 branch was 438 commits ahead of `main` at baseline; default-branch consumers may see an older project state | OPEN | Preserve current branch; plan a separately approved integration strategy and assess the large delta before any merge |
| ORCH-003 | P2 | Task planning | No central dependency graph was found in the inspected repository paths | OPEN | O2: define task dependencies without duplicating milestone details |
| ORCH-004 | P2 | Verification evidence | CI, PR, commit, instrumentation, and device evidence are not yet linked in a dedicated evidence ledger | OPEN | O3: create an evidence ledger with immutable run/commit/device references |
| ORCH-005 | P2 | Runtime / local workspace | Local working-tree state and physical-device runtime state were not accessible through the remote repository audit | OPEN | Verify local Git status and capture device evidence in an authorized environment |

Finding lifecycle: `OPEN` → `INVESTIGATING` → `FIXED` → `VERIFIED`, or `ACCEPTED RISK` / `BLOCKED`. State transitions require evidence. This table is not permission to fix findings.

## 6. Dependency roadmap

The following is a proposed coordination sequence, not approval to execute future work:

| Work item | Purpose | Depends on | State |
|---|---|---|---|
| O1 — Project State Registry | Establish one repository-level state snapshot | Existing repository baseline | IMPLEMENTED; commit and both hosted CI runs verified for O1 commit; physical-device validation is not applicable to this documentation task |
| O2 — Task Dependency Graph | Record prerequisite and downstream relationships | O1 | PROPOSED |
| O3 — Evidence Ledger | Link tasks to commits, PRs, CI, tests, and device evidence | O1; O2 recommended | PROPOSED |
| O4 — Governance Enforcement | Make authorization and scope gates explicit and auditable | O1; O2; O3 | PROPOSED |
| O5 — Session Recovery | Resume from verified repository/evidence state without repeating completed work | O1–O4 | PROPOSED |
| O6 — Duplicate Execution Guard | Prevent repeated or conflicting task execution | O1–O5 | PROPOSED |
| O7 — Orchestration Dashboard | Provide a read-oriented view of state, dependencies, findings, and evidence | O1–O6 | PROPOSED |

The sole orchestrator remains `using-agent-skills`. These work items must not create a competing orchestrator.

## 7. Governance and execution gate

Before every state-changing action, record and verify:

1. Authorization: explicit approval for this exact action.
2. Target: repository, branch, PR, device, or runtime target.
3. Scope: exact files and intended behavior.
4. Constraints: branch preservation, architecture boundaries, no-go actions.
5. Approval status: approved, pending, or blocked.

Default prohibitions unless separately approved:
- No branch creation or branch switching.
- No source-code changes outside the approved task.
- No commit, push, merge, PR/Issue mutation, or CI configuration change without explicit action-specific approval.
- No device installation, runtime/VPS/database modification, deployment, or release.
- No plugin installation or Project Instructions changes.

A prior approval is limited to its stated task and does not grant continuing authority.

## 8. Session handoff template

Copy and update this section at task boundaries; do not claim persistence until the updated content is actually committed or otherwise stored.

- **Task ID / scope:**
- **Repository / branch:**
- **Baseline SHA / current SHA:**
- **Working tree:** CLEAN / DIRTY / NOT VERIFIED
- **Files changed:**
- **Findings:** IDs and statuses
- **Decisions / approvals:**
- **PR / CI evidence:**
- **Unit / integration / instrumentation evidence:**
- **Physical-device evidence:**
- **Remaining risks / dependencies:**
- **Next permitted action:**

## 9. O1 change record

- Change: add this project state registry only.
- Target branch: `feature/p2-17-autonomous-ai-ad-interaction`.
- Baseline parent: `e7782c4ca94f1cef7b2a551e327017e0e18b8a0e`.
- Authorization: user explicitly approved O1 and then explicitly approved a commit containing only this document.
- Excluded: Android source, tests, Gradle, workflows, PRs, merges, deployments, and device/runtime operations.
- O1 implementation commit: `0280ef1b4b61a63e665cbebf96528b868cae30bb`; changed exactly this registry file.
- O1 CI evidence: Android CI run `45584844540` and P2.17 Android CI run `45584844539`, both completed successfully against the implementation commit.
- Follow-up registry update: records the confirmed O1 commit and CI evidence; its own resulting commit SHA and CI status must be checked after the write.
