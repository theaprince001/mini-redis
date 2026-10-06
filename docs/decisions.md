# Design Decisions

Trade-offs made deliberately, with a trigger for revisiting each one.

## Pipelined large replies have a ~32 MB ceiling (Week 1)

The outbound byte counter sums all pending writes on a connection. With the
32 MB hard cap, a single read event that batches more than ~32 MB of replies
disconnects the client, even if it is reading normally.

An 8 MB reply is 8,388,608 payload bytes plus the `$len\r\n` header and
trailing `\r\n`. Three fit under 32 MiB; the fourth crosses it. So: **three
max-size replies fit; the fourth does not.**

Benchmarks that need pipelined large values should use smaller values or
`-P 1`. Decoding one request per read event and stopping at
`!channel.isWritable()` is deferred.

Revisit if a specific benchmark requires pipelined 8 MB values.

## Query buffer cap is ~16 MB and approximate (Week 1)

The buffer that accumulates a partial request is capped at 16 MB. The check
runs only when a parse returns incomplete, and the buffer grows one read
chunk at a time, so a request slightly over 16 MB may pass if it completes
before any incomplete state crosses the cap. Effective limit is "about
16 MB".

MAX_BULK is 8 MB, so a command with a single max-size argument fits
comfortably. A command with two max-size arguments (a future MSET) does not
and is rejected with "Protocol error: query buffer exceeded". Every Week 1-2
command fits. Revisit before adding a command with two independently sized
bulk arguments.

Tested three ways (see `QueryBufferLimitTest`):
- Decoder rejects an oversized multi-argument request via EmbeddedChannel.
- Decoder accepts one complete max-size argument. This is the misfire check.
- A server built with a 1 KB cap rejects a 2 KB partial request, proving the
  configured value reaches the decoder.

## Output limit on normal clients (deliberate divergence, Week 1)

Real Redis applies output limits to replicas and pub/sub clients by default,
leaving normal clients effectively unlimited. MiniRedis applies a 32 MB cap
and a 30 s stall timer to every connection. Rationale: with a 2 GB heap and
no global memory budget yet, an unbounded client can exhaust the server.

Revisit if Week 6 introduces a global memory budget.

## MAX_BULK is 8 MB, not Redis's 512 MB (deliberate divergence, Week 1)

Chosen so a max-size reply fits inside the output cap and a single
misbehaving request cannot dwarf the heap. Becomes configurable when
`CONFIG GET`/`SET` lands.

## `*0\r\n` and `*-1\r\n` as client requests (deliberate divergence, Week 1)

Real Redis treats empty and null arrays sent as requests as a no-op. MiniRedis
rejects them: `parseRequest` enforces "flat array of one or more bulk strings."
Revisit no later than Week 3 when the differential harness lands, so the
harness can mark the divergence rather than fail on it.

## Command-name character filter (Week 1)

`CommandDispatcher` accepts `[A-Za-z_]+` and rejects anything else as unknown.
Correct for every command registered today. When a command with a digit or
punctuation is added, replace the character-class check with a direct lookup
against the registered map; the check exists only to avoid allocating a
String from an attacker-controlled 8 MB bulk.

## Partial frames re-parse from scratch (Week 1, accepted)

A request arriving across N TCP segments is parsed from the reader index on
every read event, giving O(N x size) work in the worst case. With the parser
limits in place the blast radius is bounded. Fix planned after Week 4:
resumable parse state carried on the channel.

## Week 4 recovery note: zero-extended AOF tail

`AofFraming.read` returns null for a run of zero bytes after a valid record,
treating it as a torn tail. The recovery loader MUST truncate the AOF file at
that offset before appending new records. Tested in Week 4.