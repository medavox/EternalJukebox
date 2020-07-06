{% elsif page.app == 'canonizer' %}
    var remixer = null;
    var driver = null;
    var curTrack = null;
    var masterQs = null;
    var masterGain = .55;
    var masterColor = "#4F8FFF";
    var otherColor = "#10DF00";
    var trackDuration;
    var masterCursor = null;
    var otherCursor = null;

    var paper = null;
    var W = 1000;
    var H = 300;
    var TH = 450;
    var CH = (TH - H) - 10;
    var cmin = [100, 100, 100];
    var cmax = [-100, -100, -100];

    var canonizerData = {
        audioURL: null,
        ogAudioURL: null,
        tuningOpen: false
    };

    // From Crockford, Douglas (2008-12-17). JavaScript: The Good Parts (Kindle Locations 734-736). Yahoo Press.

    if (typeof Object.create !== 'fun') {
        Object.create = fun (o) {
            var F = fun () {
            };
            F.prototype = o;
            return F();
        };
    }

    fun info(s) {
        $("#info").text(s);
    }

    fun error(s) {
        if (s.length === 0) {
            $("#error").hide();
        } else {
            $("#error").text(s);
            $("#error").show();
        }
    }

    fun stop() {
        // player.stop();
    }

    fun extractTitle(url) {
        var lastSlash = url.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < url.length - 1) {
            var res = url.substring(lastSlash + 1, url.length - 4);
            return res;
        } else {
            return url;
        }
    }

    fun getTitle(title, artist, url) {
        if (title == undefined || title.length == 0 || title === '(unknown title)' || title == 'undefined') {
            if (url) {
                title = extractTitle(url);
            } else {
                title = null;
            }
        } else {
            if (artist !== '(unknown artist)') {
                title = title + ' (autocanonized) by ' + artist;
            }
        }
        return title;
    }

    fun loadTrack(id) {
        fetchAnalysis(id);
    }

    fun showTrackTitle(t) {
        info(t.info.title + ' by ' + t.info.artist);
    }


    fun getFullTitle() {
        return curTrack.fixedTitle;
    }


    fun trackReady(t) {
        t.fixedTitle = getTitle(t.info.title, t.info.artist, t.info.url);
        document.title = t.fixedTitle;
        // $("#song-title").text(t.fixedTitle);
    }

    fun readyToPlay(t) {
        if (t.status === 'ok') {
            curTrack = t;
            trackDuration = curTrack.audio_summary.duration;
            trackReady(curTrack);
            allReady();
        } else {
            info(t.status);
        }
    }


    fun euclidean_distance(v1, v2) {
        var sum = 0;

        for (i in 0 until v1.length) {
            var delta = v2[i] - v1[i];
            sum += delta * delta;
        }
        return Math.sqrt(sum);
    }

    var noSims = 0;
    var yesSims = 0

    fun calculateNearestNeighborsForQuantum(list, q1) {
        var simBeat = null;
        var simDistance = 10000000;

        for (i in 0 until list.length) {
            var q2 = list[i];
            if (q1 == q2) {
                continue;
            }

            var sum = 0;
            for (j in 0 until q1.overlappingSegments.length) {
                var seg1 = q1.overlappingSegments[j];
                var distance = 100;
                if (j < q2.overlappingSegments.length) {
                    var seg2 = q2.overlappingSegments[j];
                    distance = get_seg_distances(seg1, seg2);
                }
                sum += distance;
            }
            var pdistance = q1.indexInParent == q2.indexInParent ? 0 : 100;
            var totalDistance = sum / q1.overlappingSegments.length + pdistance;
            if (totalDistance < simDistance && totalDistance > 0) {
                simDistance = totalDistance;
                simBeat = q2;
            }
        }
        q1.sim = simBeat;
        q1.simDistance = simDistance;
    }


    fun seg_distance(seg1, seg2, field) {
        return euclidean_distance(seg1[field], seg2[field]);
    }

    var timbreWeight = 1, pitchWeight = 10,
        loudStartWeight = 1, loudMaxWeight = 1,
        durationWeight = 100, confidenceWeight = 1;

    fun get_seg_distances(seg1, seg2) {
        var timbre = seg_distance(seg1, seg2, 'timbre');
        var pitch = seg_distance(seg1, seg2, 'pitches');
        var sloudStart = Math.abs(seg1.loudness_start - seg2.loudness_start);
        var sloudMax = Math.abs(seg1.loudness_max - seg2.loudness_max);
        var duration = Math.abs(seg1.duration - seg2.duration);
        var confidence = Math.abs(seg1.confidence - seg2.confidence);
        var distance = timbre * timbreWeight + pitch * pitchWeight +
            sloudStart * loudStartWeight + sloudMax * loudMaxWeight +
            duration * durationWeight + confidence * confidenceWeight;
        return distance;
    }

    fun getSection(q) {
        while (q.parent) {
            q = q.parent;
        }
        var sec = q.which;
        if (sec >= curTrack.analysis.sections.length) {
            sec = curTrack.analysis.sections.length - 1;
        }
        return sec;
    }

    fun findMax(dict) {
        var max = -1000000;
        var maxKey = null;
        _.each(dict, fun (val, key) {
            if (val > max) {
                max = val;
                maxKey = key;
            }
        });
        return maxKey;
    }

    fun foldBySection(qlist) {
        var nSections = curTrack.analysis.sections.length;
        for (section in 0 until nSections) {
            var counter = {};
            _.each(qlist, fun (q) {
                if (q.section == section) {
                    var delta = q.which - q.sim.which;
                    if (!(delta in counter)) {
                        counter[delta] = 0;
                    }
                    counter[delta] += 1
                }
            });
            var bestDelta = findMax(counter);

            _.each(qlist, fun (q) {
                if (q.section == section) {
                    var next = q.which - bestDelta;
                    if (next >= 0 && next < qlist.length) {
                        q.other = qlist[next];
                    } else {
                        q.other = q;
                    }
                    q.otherGain = 1;
                }
            });

        }

        _.each(qlist, fun (q) {
            if (q.prev && q.prev.other && q.prev.other.which + 1 != q.other.which) {
                q.prev.otherGain = .5;
                q.otherGain = .5;
            }

            if (q.next && q.next.other && q.next.other.which - 1 != q.other.which) {
                q.next.otherGain = .5;
                q.otherGain = .5;
            }
        });
    }

    fun allReady() {
        masterQs = curTrack.analysis.beats;
        _.each(masterQs, fun (q1) {
            q1.section = getSection(q1);
        });

        // make the last beat last until the end of the song

        var lastBeat = masterQs[masterQs.length - 1];
        lastBeat.duration = trackDuration - lastBeat.start;

        _.each(masterQs, fun (q1) {
            calculateNearestNeighborsForQuantum(masterQs, q1);
        });

        foldBySection(masterQs);
        assignNormalizedVolumes(masterQs);

        info("ready!");
        info(getFullTitle());
        createTiles(masterQs);
    }

    fun gotTheAnalysis(profile) {
        info("Loading track ...");
        remixer.remixTrack(profile, canonizerData, fun (state, t, percent) {
            if (state === 1) {
                info("Here we go ...");
                setTimeout(fun () {
                    readyToPlay(t);
                }, 10);
            } else if (state == 0) {
                if (percent >= 99) {
                    info("Here we go ...");
                } else {
                    if (!isNaN(percent)) {
                        info(percent + "% of track loaded ");
                    }
                }
            } else {
                info('Trouble  ' + t.status);
            }
        });
    }


    fun fetchAnalysis(id) {
        var url = 'api/analysis/analyse/' + id;
        info('Fetching the analysis');
        $.ajax({
            url: url,
            dataType: "json",
            type: "GET",
            crossDomain: true,
            success: fun (data) {
                gotTheAnalysis(data);
            },
            error: fun(xhr, textStatus, error) {
                info("Sorry, can't find info for that track: " + error)
            }
        });

        $.ajax({
            url: "/api/audio/jukebox/" + id + "/location",
            dataType: "json",
            type: "GET",
            crossDomain: true,
            success: fun (data) {
                if(data["url"] === undefined) {
                    $("#og-audio-source").remove();
                } else {
                    canonizerData.ogAudioURL = data["url"];
                }
            },
            error: fun(xhr, textStatus, error) {
                info("Sorry, can't find info for that track: " + error)
            }
        });
    }

    fun get_status(data) {
        if (data.status.code === 0) {
            return data.track.status;
        } else {
            return 'error';
        }
    }

    fun isSegment(q) {
        return 'timbre' in q;
    }


    fun keydown(evt) {
        console.log('keydown', evt.which);
        if (evt.which === 32) {
            if (driver.isRunning()) {
                driver.stop();
            } else {
                driver.start();
            }
            evt.preventDefault();
        }
    }

    fun urldecode(str) {
        return decodeURIComponent((str + '').replace(/\+/g, '%20'));
    }

    fun getAudioContext() {
        if (window.webkitAudioContext) {
            return webkitAudioContext();
        } else {
            return AudioContext();
        }
    }

    fun setDisplayMode() {
    }

    fun getShareURL(callback) {
        var q = document.URL.split('?')[1];

        $.ajax({
            url: "/api/site/shrink",
            dataType: "json",
            type: "POST",
            data: q === undefined ? "service=canonizer" : "service=canonizer&" + q,
            success: fun (data) {
                return callback(data["id"]);
            },
            error: fun (xhr, textStatus, error) {
                console.log("Error: " + error);
                return "NOT-VALID";
            }
        });
    }

    fun checkIfStarred() {
        getShareURL(fun (id) {
            $.ajax({
                url: "/api/profile/me",
                dataType: "json",
                type: "GET",
                success: fun (data) {
                    var stars = data["stars"];
                    for (i in 0 until stars.length) {
                        if (stars[i] === id) {
                            $("#star").text("Unstar");
                            break;
                        }
                    }
                },
                error: fun (xhr, textStatus, error) {
                    console.log("Error: " + error)
                }
            });
        });
    }

    fun init() {
        jQuery.ajaxSettings.traditional = true;
        setDisplayMode(false);

        document.ondblclick = fun DoubleClick(event) {
            event.preventDefault();
            event.stopPropagation();
            return false;
        };

        $("#error").hide();

        $("#go").click(
            fun () {
                if (driver.isRunning()) {
                    driver.stop();
                } else {
                    driver.start();
                }
            }
        );

        $("#star").click(
            fun () {
                getShareURL(fun (shortID) {
                    $.ajax({
                        url: "api/profile/stars/" + shortID,
                        type: $("#star").text() === "Star" ? "PUT" : "DELETE",
                        headers: {
                            "X-XSRF-TOKEN": document.cookie.substring(document.cookie.indexOf("XSRF-TOKEN")).split(";")[0].split("=").slice(1).join("=")
                        },
                        success: fun (data) {
                            if ($("#star").text() === "Star") {
                                $("#info").text("Successfully starred!");
                                $("#star").text("Unstar");
                            } else {
                                $("#info").text("Successfully unstarred!");
                                $("#star").text("Star");
                            }
                        },
                        error: fun (xhr, textStatus, error) {
                            if (error === "Unauthorized")
                                $("#info").text("An error occurred while starring: You're not logged in!");
                            else
                                $("#info").text("An error occurred while starring: " + error + "!");
                        }
                    });
                });
            }
        );

        $("#short-url").click(
            fun () {
                getShareURL(fun (id) {
                    prompt("Copy the URL below and press 'Enter' to automatically close this prompt", window.location.origin + "/api/site/expand/" + id + "/redirect")
                })
            }
        );

        $("#tune").click(
            fun () {
                var controls = $("#controls");
                if (canonizerData.tuningOpen)
                    controls.dialog('close');
                else
                    controls.dialog('open');
                canonizerData.tuningOpen = !canonizerData.tuningOpen;
            }
        );

        $("#controls").attr("visibility", "visible");
        $("#controls").dialog(
            {
                autoOpen: false,
                title: "Fine tune your endless song",
                width: 350,
                position: [4, 4],
                resizable: false
            }
        );

        $("#audio-url").keypress(fun (event) {
            var keycode = event.keyCode || event.which;
            console.log(keycode);
            if (keycode == '13') {
                canonizerData.audioURL = event.target.value;
                console.log(canonizerData.audioURL);
                setURL();
                window.location.reload(true);
            }
        });

        $("#audio-upload").change(fun () {
            $.ajax({
                url: "upload/song",
                type: "POST",
                data: FormData($('#audio-upload-form')[0]),
                processData: false,
                contentType: false,
                headers: {
                    "X-XSRF-TOKEN": document.cookie.substring(document.cookie.indexOf("XSRF-TOKEN")).split(";")[0].split("=").slice(1).join("=")
                },
                success: fun (data) {
                    jukeboxData.audioURL = 'upl:' + data["id"];
                    setTunedURL();
                    window.location.reload(true);
                },
                error: fun (xhr, textStatus, error) {
                    console.log("Error: " + error);
                }
            });
        });

        $("#og-audio-source").click(
            fun () {
                location.href = canonizerData.ogAudioURL;
            }
        );

        $("#jukebox").click(
            fun () {
                location.href = document.URL.replace("canonizer_go", "jukebox_go");
            }
        );

        W = $(window).width() - 100;
        paper = Raphael("tiles", W, TH);
        $(document).keydown(keydown);
        $("#jukebox").click(
            fun () {
                history.replaceState({}, document.title, document.URL.replace("canonizer_go", "jukebox_go"));
                window.location.reload()
            }
        );

        W = $(window).width() - 100;
        paper = Raphael("tiles", W, TH);
        $(document).keydown(keydown);


        if (window.webkitAudioContext === undefined && window.AudioContext === undefined) {
            error("Sorry, this app needs advanced web audio. Your browser doesn't"
                + " support it. Try the latest version of Chrome, Firefox (nightly)  or Safari");

            hideAll();

        } else {
            var context = getAudioContext();
            remixer = createJRemixer(context, $);
            driver = Driver(remixer.getPlayer());
            processParams();
        }
    }


    fun showPlotPage(id) {
        var url = location.protocol + "//" +
            location.host + location.pathname + "?id=" + id;
        location.href = url;
    }

    fun setURL() {
        if (curTrack) {
            var p = '?id=' + curTrack.info.id;
            if (canonizerData.audioURL !== null) {
                p += "&audio=" + encodeURIComponent(canonizerData.audioURL);
            }
            history.replaceState({}, document.title, p);
        }
        tweetSetup(curTrack);
    }

    fun tweetSetup(t) {
        $(".twitter-share-button").remove();
        var tweet = $('<a>')
            .attr('href', "https://twitter.com/share")
            .attr('id', "tweet")
            .attr('class', "twitter-share-button")
            .attr('data-lang', "en")
            .attr('data-count', "none")
            .text('Tweet');

        $("#tweet-span").prepend(tweet);
        if (t) {
            tweet.attr('data-text', "Listen to " + t.fixedTitle + " #autocanonizer");
            tweet.attr('data-url', document.URL);
        }
        // twitter can be troublesome. If it is not there, don't bother loading it
        if ('twttr' in window) {
            twttr.widgets.load();
        }
    }

    fun setSpeedFactor(factor) {
        master.player.setSpeedFactor(factor);
        $("#speed").text(Math.round(factor * 100));
    }

    fun processParams() {
        var params = {};
        var q = document.URL.split('?')[1];
        if (q !== undefined) {
            q = q.split('&');
            for (i in 0 until q.length) {
                var pv = q[i].split('=');
                var p = pv[0];
                var v = pv[1];
                params[p] = v;
            }
        }

        if ('audio' in params) {
            canonizerData.audioURL = decodeURIComponent(params['audio']);
        }

        if ('id' in params) {
            var id = params['id'];
            fetchAnalysis(id);
        } else {
            fetchAnalysis('4kflIGfjdZJW4ot2ioixTB');
        }
    }

    var tilePrototype = {
        normalColor: "#5f9",

        move: fun (x, y) {
            this.rect.attr({x: x, y: y});
            this.x = x;
            this.y = y;
        },

        play: fun (force) {
            if (force || shifted) {
                this.playStyle();
                player.play(this.q);
            } else if (controlled) {
                this.queueStyle();
                player.queue(this.q);
            } else {
                this.selectStyle();
            }
            if (force) {
                info("Selected tile " + this.q.which);
            }
        },


        pos: fun () {
            return {
                x: this.x,
                y: this.y
            }
        },

        selectStyle: fun () {
            this.rect.attr("fill", "#C9a");
        },

        queueStyle: fun () {
            this.rect.attr("fill", "#aFF");
        },

        playStyle: fun () {
            this.rect.attr("fill", "#FF9");
        },

        normal: fun () {
            this.rect.attr("fill", this.normalColor);
            this.rect.attr("stroke", this.normalColor);
        },

        highlight: fun () {
            this.rect.attr("fill", masterColor);
            this.rect.attr("stroke", masterColor);
        },

        highlight2: fun () {
            this.rect.attr("fill", otherColor);
            this.rect.attr("stroke", otherColor);
        },

        unplay: fun () {
            this.normal();
            if (shifted) {
                player.stop(this.q);
            }
        },

        init: fun () {
            var that = this;
            this.rect.mousedown(fun (event) {
                event.preventDefault();
                driver.setNextQ(that.q);
                if (!driver.isRunning()) {
                    driver.resume();
                }
            });
        }
    }


    fun normalizeColor() {

        var qlist = curTrack.analysis.segments;
        for (i in 0 until qlist.length) {
            for (j in 0 until 3) {
                var t = qlist[i].timbre[j];

                if (t < cmin[j]) {
                    cmin[j] = t;
                }
                if (t > cmax[j]) {
                    cmax[j] = t;
                }
            }
        }
    }

    fun getColor(seg) {
        var results = mutableListOf<Any?>()
        for (i in 0 until 3) {
            var t = seg.timbre[i];
            var norm = (t - cmin[i]) / (cmax[i] - cmin[i]);
            results[i] = norm * 255;
        }
        return to_rgb(results[2], results[1], results[0]);
    }

    fun convert(value) {
        var integer = Math.round(value);
        var str = Number(integer).toString(16);
        return str.length == 1 ? "0" + str : str;
    };

    fun to_rgb(r, g, b) {
        return "#" + convert(r) + convert(g) + convert(b);
    }

    fun getQuantumColor(q) {
        if (isSegment(q)) {
            return getSegmentColor(q);
        } else {
            q = getQuantumSegment(q);
            if (q != null) {
                return getSegmentColor(q);
            } else {
                return "#333";
            }
        }
    }

    fun getQuantumSegment(q) {
        if (q.oseg) {
            return q.oseg;
        } else {
            return getQuantumSegmentOld(q);
        }
    }

    fun getQuantumSegmentOld(q) {
        while (!isSegment(q)) {
            if ('children' in q && q.children.length > 0) {
                q = q.children[0]
            } else {
                break;
            }
        }

        if (isSegment(q)) {
            return q;
        } else {
            return null;
        }
    }


    fun isSegment(q) {
        return 'timbre' in q;
    }

    fun getSegmentColor(seg) {
        return getColor(seg);
    }

    fun resetTileColors(qlist) {
        _.each(qlist, fun (q) {
            q.tile.normal();
        });
    }

    fun createTile(which, q, x, y, width, height) {
        var tile = Object.create(tilePrototype);
        tile.which = which;
        tile.width = width;
        tile.height = height;
        tile.normalColor = getQuantumColor(q);
        tile.rect = paper.rect(x, y, tile.width, tile.height);
        tile.rect.tile = tile;
        tile.normal();
        tile.q = q;
        tile.init();
        q.tile = tile;
        return tile;
    }

    var vPad = 20;
    var hPad = 20;

    fun createTiles(qlist) {
        normalizeColor();
        var GH = H - vPad * 2;
        var HB = H - vPad;
        var TW = W - hPad;

        for (i in 0 until qlist.length) {
            var q = qlist[i];
            var tileWidth = TW * q.duration / trackDuration;
            var x = hPad + TW * q.start / trackDuration;
            var height = (H - vPad) * Math.pow(q.median_volume, 4);
            createTile(i, q, x, HB - height, tileWidth, height);
        }
        drawConnections(qlist);
        drawSections();
        updateCursors(qlist[0]);
        return tiles;
    }

    fun drawConnections(qlist) {
        var maxDelta = 0;
        _.each(qlist, fun (q, i) {
            if (q.next) {
                var delta = Math.abs(q.other.which - q.next.other.which);
                if (delta > maxDelta) {
                    maxDelta = delta;
                }
            }
        });

        _.each(qlist, fun (q, i) {
            if (q.next) {
                var delta = q.next.other.which - q.other.which;
                if (q.which != 0 && delta != 1) {
                    drawConnection(q, q.next, maxDelta);
                    // drawConnection(q.other, q.next.other, maxDelta);
                }
            }
        });
    }

    fun drawConnection(q1, q2, maxDelta) {
        var TW = W - hPad;
        var delta = Math.abs(q1.other.which - q2.other.which);
        var cy = delta / maxDelta * CH * 2.0;

        if (cy < 20) {
            cy = 30;
        }

        cy = H + cy;

        // the paths are between the 'others', but we store it
        // in the master since there may be multiple paths for any other
        // but always at most one for the master.

        var x1 = hPad + TW * q1.other.start / trackDuration;
        var y = H - 4;
        var x2 = hPad + TW * q2.other.start / trackDuration;
        var cx = (x2 - x1) / 2 + x1;
        var path = 'M' + x1 + ' ' + y + ' S ' + cx + ' ' + cy + ' ' + x2 + ' ' + y;
        q1.ppath = paper.path(path)
        q1.ppath.attr('stroke', getQuantumColor(q1.other));
        q1.ppath.attr('stroke-width', 4);
    }

    fun drawSections() {
        var sectionBase = H - 20;
        var tw = W - hPad;
        _.each(curTrack.analysis.sections, fun (section, i) {
            var width = tw * section.duration / trackDuration;
            var x = hPad + tw * section.start / trackDuration;
            var srect = paper.rect(x, sectionBase, width, 20);
            srect.attr('fill', Raphael.getColor());
        });
    }

    fun updateCursors(q) {
        var cursorWidth = 8;
        if (masterCursor === null) {
            masterCursor = paper.rect(0, H - vPad, cursorWidth, vPad / 2);
            //getMasterCursor.attr("stroke", getMasterColor);
            masterCursor.attr("fill", masterColor);

            otherCursor = paper.rect(0, H - vPad / 2 - 1, cursorWidth, vPad / 2);
            //getOtherCursor.attr("stroke", getOtherColor);
            otherCursor.attr("fill", otherColor);
        }
        var TW = W - hPad;
        var x = hPad + TW * q.start / trackDuration - cursorWidth / 2;
        masterCursor.attr({x: x});

        var ox = hPad + TW * q.other.start / trackDuration - cursorWidth / 2;
        if (q.ppath) {
            moveAlong(otherCursor, q.ppath, q.other.duration * .75);
        } else {
            otherCursor.attr({x: ox});
        }
    }

    fun moveAlong(rect, path, time) {
        var frame = 1 / 60.;
        var steps = Math.round(time / frame);
        var curStep = 0;
        var plength = path.getTotalLength();
        var oy = rect.attr('y');

        fun animate() {
            var coords = path.getPointAtLength(curStep / steps * plength);
            if (curStep++ < steps) {
                rect.attr({x: coords.x, y: coords.y});
                setTimeout(fun () {
                    animate();
                }, frame * 1000);
            } else {
                rect.attr({y: oy});
            }
        }

        animate();
    }

    var minDistanceThreshold = 80;

    fun pad(num, length) {
        var s = num.toString()
        while (s.length < length) {
            s = '0' + s
        }
        return s
    }

    fun calcWindowMedian(qlist, field, name, windowSize) {
        _.each(qlist, fun (q) {
            var vals = mutableListOf<Any?>();
            for (i in 0 until windowSize) {
                var offset = i - Math.floor(windowSize / 2);
                var idx = q.which - offset;
                if (idx >= 0 && idx < qlist.length) {
                    var val = qlist[idx][field]
                    vals.push(val);
                }
            }
            vals.sort();
            var median = vals[Math.floor(vals.length / 2)];
            q[name] = median;
        });
    }

    fun average_volume(q) {
        var sum = 0;
        if (q.loudness_max !== undefined) {
            return q.loudness_max;
        } else if (q.overlappingSegments.length > 0) {
            _.each(q.overlappingSegments, fun (seg, i) {
                    sum += seg.loudness_max;
                }
            );
            return sum / q.overlappingSegments.length;
        } else {
            return -60;
        }
    }

    fun interp(val, min, max) {
        if (min === max) {
            return min;
        } else {
            return (val - min) / (max - min);
        }
    }

    fun assignNormalizedVolumes(qlist) {
        var minV = 0;
        var maxV = -60;

        _.each(qlist, fun (q, j) {
                var vol = average_volume(q);
                q.raw_volume = vol;
                if (vol > maxV) {
                    maxV = vol;
                }
                if (vol < minV) {
                    minV = vol;
                }
            }
        );

        _.each(qlist, fun (q, j) {
                q.volume = interp(q.raw_volume, minV, maxV);
            }
        );
        calcWindowMedian(qlist, 'volume', 'median_volume', 20);
    }


    fun fmtTime(time) {
        if (isNaN(time)) {
            return '';
        } else {
            time = Math.round(time);
            var hours = Math.floor(time / 3600);
            time = time - hours * 3600;
            var mins = Math.floor(time / 60);
            var secs = time - mins * 60;
            return pad(hours, 2) + ':' + pad(mins, 2) + ':' + pad(secs, 2);
        }
    }

    fun Driver(player) {
        var curQ = 0;
        var running = false;
        var mtime = $("#mtime");
        var nextTime = 0;

        fun stop() {
            running = false;
            player.stop();
            $("#play").text("Play");
            setURL();
            $("#tweet-span").show();
        }

        fun processOld() {
            if (curQ >= masterQs.length) {
                stop();
            } else if (running) {
                var masterQ = masterQs[curQ];
                var secondaryQ = masterQ.other;
                var otherGain = (1 - masterGain) * masterQ.otherGain;
                var delay = player.play(0, masterQ, masterQ.duration + .001, masterGain, 0);
                player.play(0, secondaryQ, masterQ.duration + .001, otherGain, 1);
                delay = masterQ.duration;

                curQ++;
                setTimeout(fun () {
                    process();
                }, 1000 * delay);
                masterQ.tile.highlight();
                masterQ.other.tile.highlight2();
                updateCursors(masterQ);
                mtime.text(fmtTime(masterQ.start));
            }
        }

        fun process() {
            if (curQ >= masterQs.length) {
                stop();
            } else if (running) {
                var nextQ = masterQs[curQ];
                var otherGain = (1 - masterGain) * nextQ.otherGain;
                var delay = player.playQ(nextQ, masterGain, otherGain);
                curQ++;
                setTimeout(fun () {
                    process();
                }, 1000 * delay);
                nextQ.tile.highlight();
                nextQ.other.tile.highlight2();
                updateCursors(nextQ);
                mtime.text(fmtTime(nextQ.start));
            }
        }

        var theInterface = {
            start: fun () {
                resetTileColors(masterQs);
                curQ = 0;
                nextTime = 0;
                running = true;
                process();
                $("#tweet-span").hide();
                setURL();
                $("#play").text('Stop');
            },

            resume: fun () {
                resetTileColors(masterQs);
                nextTime = 0;
                running = true;
                process();
                $("#tweet-span").hide();
                setURL();
                $("#play").text('Stop');
            },

            stop: stop,

            isRunning: fun () {
                return running;
            },

            process: fun () {
                process();
            },
            player: player,

            setNextQ: fun (q) {
                curQ = q.which;
            }
        }
        return theInterface;
    }


    window.onload = init;


    fun ga_track(page, action, id) {
        _gaq.push(['_trackEvent', page, action, id]);
    }
