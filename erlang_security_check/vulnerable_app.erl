-module(vulnerable_app).
-export([start/0]).

start() ->
    io:format("Starting Vulnerable App (using file:script/1)...~n"),
    case file:script("payload.cfg") of
        {ok, Result} ->
            io:format("Script executed successfully. Result: ~p~n", [Result]);
        {error, Reason} ->
            io:format("Error executing script: ~p~n", [Reason])
    end.
