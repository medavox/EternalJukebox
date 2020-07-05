package org.abimon.eternalJukebox

class Driver(player:Any?) {
    var curTile = null;
    var curOp = null;
    var incr = 1;
    var nextTile = null;
    var bounceSeed = null;
    var bounceCount = 0;
    var nextTime = 0;
    var lateCounter = 0;
    var lateLimit = 4;

    var beatDiv = $("#beats");
    // var playcountDiv = $("#playcount");
    var timeDiv = $("#time");

    fun next() {
        if (curTile == null || curTile == undefined) {
            return jukeboxData.tiles[0];
        } else {
            var nextIndex;
            if (shifted) {
                if (bounceSeed == null) {
                    bounceSeed = curTile;
                    bounceCount = 0;
                }
                if (bounceCount++ % 2 == 1) {
                    return selectNextNeighbor(bounceSeed);
                } else {
                    return bounceSeed;
                }
            }
            if (controlled) {
                return curTile;
            } else {
                if (bounceSeed != null) {
                    var nextTile = bounceSeed;
                    bounceSeed = null;
                    return nextTile;
                } else {
                    nextIndex = curTile.which + incr
                }
            }

            if (nextIndex < 0) {
                return jukeboxData.tiles[0];
            } else if (nextIndex >= jukeboxData.tiles.length) {
                curOp = null;
                player.stop();
            } else {
                return selectRandomNextTile(jukeboxData.tiles[nextIndex]);
            }
        }
    }

    fun selectRandomNextTile(seed:Any?) {
        if (seed.q.neighbors.length == 0) {
            return seed;
        } else if (shouldRandomBranch(seed.q)) {
            var next = seed.q.neighbors.shift();
            jukeboxData.lastThreshold = next.distance;
            seed.q.neighbors.add(next);
            var tile = next.dest.tile;
            return tile;
        } else {
            return seed;
        }
    }

    fun selectRandomNextTileNew(seed:Any?) {
        if (seed.q.neighbors.length == 0) {
            return seed;
        } else if (shouldRandomBranch(seed.q)) {
            var start = window.performance.now();
            var tc = findLeastPlayedNeighbor(seed, 5);
            var tile = tc[0];
            var score = tc[1];
            var delta = window.performance.now() - start;
            //console.log('lhd ', seed.which, tile.which, score, delta);
            return tile;
        } else {
            return seed;
        }
    }

    /**
     * we look for the path to the tile that will bring
     * us to the least played tile in the future (we look
     * at lookAhead beats into the future
     */
    fun findLeastPlayedNeighbor(seed:Any?, lookAhead:Any?) {
        var nextTiles = mutableListOf<Any?>();

        if (seed.q.which != jukeboxData.lastBranchPoint) {
            nextTiles.add(seed);
        }
        seed.q.neighbors.forEach(
                fun (edge, which) {
                    var tile = edge.dest.tile;
                    nextTiles.add(tile);
                }
        );

        nextTiles = _.shuffle(nextTiles);

        if (lookAhead == 0) {
            var minTile = null;
            nextTiles.forEach(fun (tile) {
                if (minTile == null || tile.playCount < minTile.playCount) {
                    minTile = tile;
                }
            });
            return [minTile, minTile.playCount];
        } else {
            var minTile = null;
            nextTiles.forEach(fun (tile) {
                var futureTile = findLeastPlayedNeighbor(tile, lookAhead - 1);
                if (minTile == null || futureTile[1] < minTile[1]) {
                    minTile = futureTile;
                }
            });
            return minTile;
        }
    }

    fun selectNextNeighbor(seed:Any?) {
        if (seed.q.neighbors.length == 0) {
            return seed;
        } else {
            var next = seed.q.neighbors.shift();
            seed.q.neighbors.add(next);
            var tile = next.dest.tile;
            return tile;
        }
    }

    fun shouldRandomBranch(q:Any?) {
        if (jukeboxData.infiniteMode) {
            if (q.which == jukeboxData.lastBranchPoint) {
                return true;
            }

            // return true; // TEST, remove

            jukeboxData.curRandomBranchChance += jukeboxData.randomBranchChanceDelta;
            if (jukeboxData.curRandomBranchChance > jukeboxData.maxRandomBranchChance) {
                jukeboxData.curRandomBranchChance = jukeboxData.maxRandomBranchChance;
            }
            var shouldBranch = Math.random() < jukeboxData.curRandomBranchChance;
            if (shouldBranch) {
                jukeboxData.curRandomBranchChance = jukeboxData.minRandomBranchChance;
            }
            return shouldBranch;
        } else {
            return false;
        }
    }

    fun updateStats() {
        beatDiv.text(jukeboxData.beatsPlayed);
        timeDiv.text(secondsToTime((now() - startTime) / 1000.));
        /*
         if (curTile) {
         playcountDiv.text(curTile.playCount);
         }
         */
    }


    fun process() {
        if (curTile !== null && curTile !== undefined) {
            curTile.normal();
        }

        if (curOp) {
            var lastTile = curTile;
            if (nextTile != null) {
                curTile = nextTile;
                nextTile = null;
            } else {
                curTile = curOp();
            }

            if (curTile) {
                var ctime = player.curTime();
                // if we are consistently late we should shutdown
                if (ctime > nextTime) {
                    lateCounter++;
                    if (lateCounter++ > lateLimit && windowHidden()) {
                        info("Sorry, can't play music properly in the background");
                        interface.stop();
                        return;
                    }
                } else {
                    lateCounter = 0;
                }

                nextTime = player.play(nextTime, curTile.q);

                if (fastMode) {
                    nextTime = 0; // set to zero for speedup sim mode
                }
                curTile.playCount += 1;

                var delta = nextTime - ctime;
                setTimeout(fun () {
                    process();
                }, 1000 * delta - 10);

                var didJump = false;
                if (lastTile && lastTile.which != curTile.which - 1) {
                    didJump = true;
                }

                curTile.playStyle(didJump);
                jukeboxData.beatsPlayed += 1;
                updateStats();
            }
        } else {
            if (curTile != null) {
                curTile.normal();
            }
        }
    }

    fun resetPlayCounts() {
        for (i in 0 until jukeboxData.tiles.length) {
            jukeboxData.tiles[i].playCount = 0;
        }
        curGrowFactor = 1;
        redrawTiles();
    }

    var startTime = 0;
    return {
        start: fun () {
        jukeboxData.beatsPlayed = 0;
        nextTime = 0;
        bounceSeed = null;
        jukeboxData.infiniteMode = true;
        jukeboxData.curRandomBranchChance = jukeboxData.minRandomBranchChance;
        lateCounter = 0;
        curOp = next;
        startTime = now();
        $("#go").text("Stop");
            error("");
            info("");
        resetPlayCounts();
        process();
    },

        stop: fun () {
        var delta = now() - startTime;
        $("#go").text("Play");
        if (curTile) {
            curTile.normal();
            curTile = null;
        }
        curOp = null;
        bounceSeed = null;
        incr = 1;
        player.stop();
    },

        isRunning: fun () {
        return curOp !== null;
    },

        getIncr: fun () {
        return incr;
    },

        getCurTile: fun () {
        return curTile;
    },

        setIncr: fun (inc) {
        incr = inc;
    },

        setNextTile: fun (tile) {
        nextTile = tile;
    }
    };
}