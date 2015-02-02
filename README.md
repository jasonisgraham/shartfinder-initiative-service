# shartfinder-initiative-service

initiative service for shartfinder microservice geek lunch project

## Installation & Running
`git clone https://github.com/jasonisgraham/shartfinder-initiative-service`

`lein deps`

`lein trampoline run`

`http://localhost:$PORT ($PORT defaults to 5000)`

## Examples

http://localhost:$PORT spits out a bunch of JSON about initiative statuses

## About
* subscribes to `encounter-created`
* subscribes to `roll-initiative`
* publishes `initiative-created` when all players from `encounter-created` have rolled initiative

## TODO
* keep track of encounter-id so more than 1 encounter can be maintained
* use `combatant-service` when it gets created
