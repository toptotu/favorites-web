# Erlang Configuration Code Execution Check

To determine if an Erlang backend application executes code from its configuration file (`.cfg`, `.config`, etc.), you can use the following methods.

## Method 1: Source Code Analysis (Whitebox)

Check how the application reads the configuration file.

### Safe: `file:consult/1`
If the application uses `file:consult/1`, it only reads Erlang **terms** (tuples, lists, atoms, numbers, strings). It does **not** execute functions.

```erlang
% Safe usage
{ok, Terms} = file:consult("app.cfg").
```

### Unsafe: `file:script/1` or `file:eval/1`
If the application uses `file:script/1`, it evaluates the file as an Erlang script. This means any function call in the file will be executed.

Common in dynamic configuration files (often ending in `.config` or `.script`) where developers want to read environment variables (e.g., `os:getenv/1`).

```erlang
% Unsafe usage
{ok, Result} = file:script("app.cfg").
```

## Method 2: Blackbox Testing (Payload Injection)

If you cannot see the source code, you can try to inject a payload into the configuration file that produces a visible side effect.

### Steps:
1.  Locate the configuration file (e.g., `sys.config`, `app.cfg`).
2.  Backup the original file.
3.  Append or insert a harmless side-effect command, such as printing to stdout or creating a file.

### Test Payload
Insert the following line into the config file:

```erlang
io:format("WARNING: CONFIG CODE EXECUTED~n").
```

Or a more persistent check (creating a file):

```erlang
os:cmd("touch /tmp/erlang_cfg_vuln").
```

### Interpretation:
1.  **Restart the application.**
2.  **Check logs/output**:
    *   If you see "WARNING: CONFIG CODE EXECUTED" in the console or logs, the code **was executed**.
    *   If the application crashes with a syntax error or parse error, it is likely using `file:consult/1` (Safe), because `io:format(...)` is not a valid static term.
    *   If the file `/tmp/erlang_cfg_vuln` exists, the code **was executed**.

## Reproduction Scripts

In this directory, we provide two scripts to demonstrate the difference:
- `safe_app.erl`: Uses `file:consult/1`.
- `vulnerable_app.erl`: Uses `file:script/1`.
- `payload.cfg`: A config file with executable code.
