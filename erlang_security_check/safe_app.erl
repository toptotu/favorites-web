-module(safe_app).
-export([start/0]).

start() ->
    io:format("Starting Safe App (using file:consult/1)...~n"),
    case file:consult("payload.cfg") of
        {ok, Terms} ->
            io:format("Read terms successfully: ~p~n", [Terms]);
        {error, Reason} ->
            io:format("Failed to read config (Expected behavior for executable code): ~p~n", [Reason])
    end.
