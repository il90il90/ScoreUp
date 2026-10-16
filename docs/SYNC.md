# ScoreUp sync protocol

Goal: the watch is the primary scoring remote; phone and watch must show the same match, the same
scores, and never lose or double-count a point. This must hold through flaky Bluetooth, app
restarts, offline periods and rapid taps.

## Model

- Every action (a point, an undo, a rename, clock start…) is an immutable **event** with a globally
  unique id `(deviceId, seq)`. `seq` increases per device per match and is persisted, so ids are
  never reused after a restart.
- Events carry a **Lamport timestamp**. The match state is a deterministic fold of all events,
  sorted by `(lamport, device, seq)`. The same set of events gives the same state on every device.
- The event set is a grow-only set (a CRDT). Merging is set union: commutative, associative and
  idempotent. **Re-delivering an event can never change the score twice.**
- Undo is an event that revokes a target id. Two devices undoing the same point concurrently
  revoke it once.
- The active match is identified by a `MatchHeader`, last-writer-wins by `(lamport, device)`.
  Fresh installs start on a shared deterministic `initial` match, so a newly installed watch never
  displaces a match already in progress.

## Messages (JSON over the Wear `MessageClient`, path `/scoreup/sync/v1`)

- `Events{header, events[]}` is sent eagerly for every local action (fast path).
- `Summary{header, vector}` says "this is what I have": for each device, the highest *contiguous*
  seq received. The receiver sends back exactly the missing events, and asks for anything the
  peer has that it lacks. Gaps (lost messages) are re-requested automatically.
- An anti-entropy summary is exchanged on connect / resume and every 5 s while a peer is reachable.
  Missed messages heal within seconds.

## Durability

Each device persists its session (active match + the last 3 previous match logs) with
`AtomicFile` **before** broadcasting, so a crash right after a tap loses nothing. Late events for a
previous match (scored offline before the other device started a new match) are still merged into
that match and its history entry is updated. They are never applied to the new match.

## Verified by

`core/src/test/.../SyncTest.kt`: duplicate delivery, offline scoring on both sides, concurrent
undo, new-match handover, late events for a previous match, malformed messages, own-echo, and a
30-seed chaos test with 30% drops, 30% duplicates, random reordering and network flapping.
