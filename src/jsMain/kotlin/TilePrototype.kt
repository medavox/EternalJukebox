package org.abimon.eternalJukebox

class TilePrototype {

    constructor(which:Any?, q:Any?, height:Any?, width:Any?) {
        var padding = 0;
        this.which = which;
        this.width = width;
        this.height = height;
        this.branchColor = getBranchColor(q);
        this.quantumColor = getQuantumColor(q);
        this.normalColor = this.quantumColor;
        this.isPlaying = false;
        this.isScaled = false;
        this.playCount = 0;

        this.rect = paper.rect(0, 0, this.width, this.height);
        this.rect.attr("stroke", this.normalColor);
        this.rect.attr("stroke-width", 0);
        this.q = q;
        q.tile = this;
        this.normal();
    }
    init {
        this.rect.mouseover(fun (event) {
            this.playStyle(false);
            if (debugMode) {
                if (that.q.which > jukeboxData.lastBranchPoint) {
                    $("#beats").text(this.q.which + ' " + that.q.reach + "*');
                } else {
                    var qlength = track.analysis.beats.length;
                    var distanceToEnd = qlength - this.q.which;
                    $("#beats").text(this.q.which + ' ' + this.q.reach
                            + ' ' + Math.floor((this.q.reach - distanceToEnd) * 100 / qlength));
                }
            }
            event.preventDefault();
        });

        this.rect.mouseout(fun (event) {
            this.normal();
            event.preventDefault();
        });

        this.rect.mousedown(fun (event) {
            event.preventDefault();
            driver.setNextTile(that);
            if (!driver.isRunning()) {
                driver.start();
            }
            if (controlled) {
                driver.setIncr(0);
            }
        });
    }
    var normalColor = "#5f9"

    fun move (x:Any?, y:Any?) {
        this.rect.attr({x: x, y: y});
        if (this.label) {
            this.label.attr({x: x + 2, y: y + 8});
        }
    }

    fun rotate (angleRadians:Double) {
        val angleDegrees = 360 * (angleRadians / (Math.PI * 2));
        this.rect.transform('r' + angleDegrees);
    }

    fun play (force:Boolean) {
        if (force || shifted) {
            this.playStyle(true);
            player.play(0, this.q);
        } else if (controlled) {
            this.queueStyle();
            player.queue(this.q);
        } else {
            this.selectStyle();
        }
        if (force) {
            info("Selected tile " + this.q.which);
            jukeboxData.selectedTile = this;
        }
    }


    fun selectStyle () {
        this.rect.attr("fill", "#C9a");
    }

    fun queueStyle () {
        this.rect.attr("fill", "#aFF");
    }

    fun pauseStyle () {
        this.rect.attr("fill", "#F8F");
    }

    fun playStyle (didJump:Any?) {
        if (!this.isPlaying) {
            this.isPlaying = true;
            if (!this.isScaled) {
                this.isScaled = true;
                this.rect.attr("width", maxTileWidth);
            }
            this.rect.toFront();
            this.rect.attr("fill", highlightColor);
            highlightCurves(this, true, didJump);
        }
    }


    fun normal () {
        this.rect.attr("fill", this.normalColor);
        if (this.isScaled) {
            this.isScaled = false;
            //this.rect.scale(1/1.5, 1/1.5);
            var newWidth = Math.round((minTileWidth + this.playCount * growthPerPlay) * curGrowFactor);
            if (newWidth < 1) {
                newWidth = 1;
            }
            if (newWidth > 90) {
                curGrowFactor /= 2;
                redrawTiles();
            } else {
                this.rect.attr("width", newWidth);
            }
        }
        highlightCurves(this, false, false);
        this.isPlaying = false;
    }


}