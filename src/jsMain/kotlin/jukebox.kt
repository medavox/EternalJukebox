import kotlin.math.*
import externaljs.jquery.*
import externaljs.jquery.JQueryUI.DialogOptions
import externaljs.jquery.JQueryUI.SliderOptions
import externaljs.jquery.JQueryUI.SliderUIParams
import externaljs.raphael.global.Raphael
import externaljs.typescript.AudioContext
import externaljs.typescript.BaseAudioContext
import org.w3c.dom.HTMLElement
import org.w3c.dom.url.URL
import org.w3c.xhr.FormData
import org.w3c.xhr.XMLHttpRequest
import kotlin.browser.*
import kotlin.js.Date
import kotlin.math.round

// This code will make you cry. It was written in a mad
// dash during Music Hack Day Boston 2012, and has
// quite a bit of hackage of the bad kind in it.

lateinit var remixer:JRemixer
var player:Any? = null
var driver:Any? = null
var track:Any? = null
var W = 900
var H = 680
var paper:Any? = null

// configs for chances to branch
var defaultMinRandomBranchChance = .18
var defaultMaxRandomBranchChance = .5

var defaultRandomBranchChanceDelta = .018;
var minRandomBranchChanceDelta = .000;
var maxRandomBranchChanceDelta = .200;

var highlightColor = "#0000ff";
var jumpHighlightColor = "#00ff22";
var selectColor = "#ff0000";
var uploadingAllowed = false;
var debugMode = true;
var fastMode = false;

var shifted = false;
var controlled = false;

var minTileWidth = 10;
var maxTileWidth = 90;
var growthPerPlay = 10;
var curGrowFactor = 1;



class jukeboxData { companion object {
    var infiniteMode = true      // if true, allow branching
    var maxBranches = 4        // max branches allowed per beat
    var maxBranchThreshold = 80 // max allowed distance threshold

    var computedThreshold = 0   // computed best threshold
    var currentThreshold = 0    // current in-use max threshold
        set(newValue) {
            field = newValue
            jQuery("#threshold").text(newValue);
            jQuery("#threshold-slider").slider("value", newValue);
        }
    var addLastEdge = true      // if true, optimize by adding a good last edge
        set(newValue) {
            field = newValue
            jQuery("#last-branch").attr("checked", newValue.toString());
            setTunedURL();
        }
    var justBackwards = false   // if true, only add backward branches
        set(newValue) {
            field = newValue
            jQuery("#reverse-branch").attr("checked", newValue.toString());
            setTunedURL();
        }
    var justLongBranches = false
        // if true, only add long branches
        set(newValue) {
            field = newValue
            jQuery("#long-branch").attr("checked", newValue.toString());
            setTunedURL();
        }
    var removeSequentialBranches = false
        // if true, remove consecutive branches of the same distance
        set(newValue) {
            field = newValue
            jQuery("#sequential-branch").attr("checked", newValue.toString());
            setTunedURL();
        }

    var deletedEdgeCount = 0    // number of edges that have been deleted
        set(newValue) {
            field = newValue
            jQuery("#deleted-branches").text(newValue);
        }

    var lastBranchPoint = 0    // last beat with a good branch
    var longestReach = 0.0       // longest looping secstion
        set(newValue) {
            field = newValue
            jQuery("#loop-length-percent").text(round(newValue));
            var loopBeats = round(newValue * totalBeats / 100);
            jQuery("#loop-length-beats").text(loopBeats);
            jQuery("#total-beats").text(totalBeats);
        }

    var beatsPlayed = 0          // total number of beats played
    var totalBeats = 0         // total number of beats in the song
        set(newValue) {
            field = newValue
            jQuery("#total-beats").text(newValue);
        }
    var branchCount = 0         // total number of active branches
        set(newValue) {
            field = newValue
            jQuery("#branch-count").text(newValue);
        }

    var selectedTile = null    // current selected tile
    var selectedCurve = null   // current selected branch

    var tiles = mutableListOf<Any?>()              // all of the tiles
    var allEdges = mutableListOf<Any?>()           // all of the edges
    var deletedEdges = mutableListOf<Any?>()       // edges that should be deleted

    var audioURL = null        // The URL to play audio from; null means default
        set(newValue) {
            field = newValue
            jQuery("#audio-url").`val`(decodeURIComponent(newValue));
        }
    var trackID = null
    var ogAudioURL = null

    var minRandomBranchChance = 0.0
        set(newValue) {
            field = newValue
            jQuery("#min-prob").text(round(newValue * 100));
            jQuery("#probability-slider").slider("values",
                    arrayOf<Number>(newValue * 100, maxRandomBranchChance * 100));
            curRandomBranchChance = clamp(curRandomBranchChance,
                    newValue, maxRandomBranchChance);
        }
    var maxRandomBranchChance = 0.0
        set(newValue) {
            field = newValue
            jQuery("#max-prob").text(round(newValue * 100));
            jQuery("#probability-slider").slider("values",
                    arrayOf<Number>(minRandomBranchChance * 100, newValue * 100));
            curRandomBranchChance = clamp(curRandomBranchChance,
                    minRandomBranchChance, newValue);
        }
    var randomBranchChanceDelta = 0.0
        set(newValue) {
            field = newValue
            var `val` = round(map_value_to_percent(newValue,
                    minRandomBranchChanceDelta, maxRandomBranchChanceDelta));
            jQuery("#ramp-speed").text(`val`);
            jQuery("#probabiltiy-ramp-slider").slider("value", `val`);
        }
    var curRandomBranchChance = 0.0
        set(newValue) {
            field = newValue
            jQuery("#branch-chance").text(round(newValue * 100));
        }
    var lastThreshold = 0.0
        set(newValue) {
            field = newValue
            jQuery("#last-threshold").text(round(newValue));
        }

    var tuningOpen = false
    var disableKeys = false
        set(newValue) {
            field = newValue
            jQuery("#disable-keys").attr("checked", newValue.toString());
            setTunedURL();
        }
}}

fun info(s:String) {
    jQuery("#info").text(s);
}


fun error(s:String) {
    if (s.length == 0) {
        jQuery("#error").hide();
    } else {
        jQuery("#error").text(s);
        jQuery("#error").show();
    }
}

fun setDisplayMode(playMode:Boolean) {
    if (playMode) {
        jQuery("#song-div").hide();
        jQuery("#select-track").hide();
        jQuery("#running").show();
        jQuery(".rotate").hide();
    } else {
        jQuery("#song-div").show();
        jQuery("#select-track").show();
        jQuery("#running").hide();
        jQuery(".rotate").show();
    }
    info("");
}

fun hideAll() {
    jQuery("#song-div").hide();
    jQuery("#select-track").hide();
    jQuery("#running").hide();
    jQuery(".rotate").hide();
}


fun stop() {
    player.stop();
    player = remixer.getPlayer();
}

fun createTiles(qtype:Any?) = createTileCircle(qtype, 250.0)

fun createTileCircle(qtype:Any?, radius:Double):MutableList<Any?> {
    var start = now();
    var y_padding = 90;
    var x_padding = 200;
    var maxWidth = 90;
    var tiles = mutableListOf<Any?>();
    var qlist = track.analysis[qtype];
    var n:Int = qlist.length
    var R = radius;
    var alpha = PI * 2 / n;
    var perimeter:Double = 2 * n * R * sin(alpha / 2)
    var a = perimeter / n;
    var width = a * 20;
    var angleOffset = -PI / 2;
    // var angleOffset = 0;

    if (width > maxWidth) {
        width = maxWidth.toDouble()
    }

    width = minTileWidth.toDouble()

    paper.clear();

    var angle = angleOffset;
    for (i in 0 until qlist.length) {
        var tile = createNewTile(i, qlist[i], a, width);
        var y = y_padding + R + R * sin(angle);
        var x = x_padding + R + R * cos(angle);
        tile.move(x, y);
        tile.rotate(angle);
        tiles.add(tile);
        angle += alpha;
    }

    // now connect every tile to its neighbors

    // a horrible hack until I figure out
    // geometry
    var roffset = width / 2;
    var yoffset = width * .52;
    var xoffset = width * 1;
    var center = " S 450 350 ";
    var branchCount = 0;
    R -= roffset;
    for (i in 0 until tiles.size) {
        var startAngle = alpha * i + angleOffset;
        var tile = tiles[i];
        var y1 = y_padding + R + R * sin(startAngle) + yoffset;
        var x1 = x_padding + R + R * cos(startAngle) + xoffset;

        for (j in 0 until tile.q.neighbors.length) {
            var destAngle = alpha * tile.q.neighbors[j].dest.which + angleOffset;
            var y2 = y_padding + R + R * sin(destAngle) + yoffset;
            var x2 = x_padding + R + R * cos(destAngle) + xoffset;

            var path = "M" + x1 + " " + y1 + center + x2 + " " + y2
            var curve = paper.path(path);
            curve.edge = tile.q.neighbors[j];
            addCurveClickHandler(curve);
            highlightCurve(curve, false, false);
            tile.q.neighbors[j].curve = curve;
            branchCount++;
        }
    }
    jukeboxData.branchCount = branchCount;
    return tiles;
}

fun addCurveClickHandler(curve:JQuery) {
    curve.click(
            fun () {
                if (jukeboxData.selectedCurve) {
                    highlightCurve(jukeboxData.selectedCurve, false, false);
                }
                selectCurve(curve, true);
                jukeboxData.selectedCurve = curve;
            });

    curve.mouseover{ highlightCurve(curve, true, false) }

    curve.mouseout {
        if (curve != jukeboxData.selectedCurve) {
            highlightCurve(curve, false, false);
        }
    }
}

fun highlightCurve(curve:JQuery?, enable:Boolean, jump:Boolean) {
    if (curve != null) {
        if (enable) {
            var color = if(jump) jumpHighlightColor else highlightColor;
            curve.attr("stroke-width", 4);
            curve.attr("stroke", color);
            curve.attr("stroke-opacity", 1.0);
            curve.toFront();
        } else {
            if (curve.edge) {
                curve.attr("stroke-width", 3);
                curve.attr("stroke", curve.edge.src.tile.quantumColor);
                curve.attr("stroke-opacity", .7);
            }
        }
    }
}

fun selectCurve(curve:JQuery) {
    curve.attr("stroke-width", 6);
    curve.attr("stroke", selectColor);
    curve.attr("stroke-opacity", 1.0);
    curve.toFront();
}


fun extractTitle(url:String):String {
    var lastSlash = url.lastIndexOf('/');
    if (lastSlash >= 0 && lastSlash < url.length - 1) {
        var res = url.substring(lastSlash + 1, url.length - 4);
        return res;
    } else {
        return url;
    }
}

fun getTitle(title:String, artist:Any?, url:String?):String? {
    var workingTitle:String? = title
    if (title == null || title.length == 0 || title == "(unknown title)" || title == "undefined") {
        if (url != null) {
            workingTitle = extractTitle(url);
        } else {
            workingTitle = null;
        }
    } else {
        if (artist !== "(unknown artist)") {
            workingTitle = workingTitle + " by " + artist;
        }
    }
    return workingTitle;
}


fun trackReady(t:Any?) {
    t.fixedTitle = getTitle(t.info.title, t.info.artist, t.info.url);
    document.title = "Eternal Jukebox for " + t.fixedTitle;
    jQuery("#song-title").text(t.fixedTitle);
    jQuery("#song-url").attr("href", "https://open.spotify.com/track/" + t.info.id);
    jukeboxData.minLongBranch = track.analysis.beats.length / 5;
}


fun readyToPlay(t:Any?) {
    setDisplayMode(true);
    driver = Driver(player);
    info("Ready!");
    normalizeColor();
    trackReady(t);
    drawVisualization();
}

fun drawVisualization() {
    if (track != null) {
        if (jukeboxData.currentThreshold == 0) {
            dynamicCalculateNearestNeighbors("beats");
        } else {
            calculateNearestNeighbors("beats", jukeboxData.currentThreshold);
        }
        createTilePanel("beats");
    }
}


fun gotTheAnalysis(profile:Any?) {
    info("Loading track ...");
    remixer.remixTrack(profile, fun (state:Int, t:Any?, percent:Float) {
        track = t;
        if (isNaN(percent)) {
            percent = 0;
        }
        if (state == 1) {
            info("Calculating pathways through the song ...");
            setTimeout(fun () {
                readyToPlay(t);
            }, 10);
        } else if (state == 0) {
            if (percent >= 99) {
                info("Calculating pathways through the song ...");
            } else {
                if (percent > 0) {
                    info(percent + "% of track loaded ");
                } else {
                    info("Loading the track ");
                }
            }
        } else {
            info("Trouble  " + t.status);
            setDisplayMode(false);
        }
    });
}


fun listSong(r:Any?):JQuery? {
    var title = getTitle(r.title, r.artist, null);
    var item = null;
    if (title != null) {
        var item = jQuery("<li>").append(title);

        item.attr("class", "song-link");
        item.click(fun () {
            showPlotPage(r.id);
        });
    }
    return item;
}

fun listSongAsAnchor(r:Any?):JQuery? {
    var title = getTitle(r.title, r.artist, r.url);
    var item = jQuery("<li>").html("<a href=\"index.html?id=\"" + r.id + "\">" + title + "</a>")
    return item;
}

fun listTracks(active:Any?, tracks:List<Any?>) {
    jQuery("#song-div").show();
    jQuery("#song-list").empty();
    jQuery(".sel-list").removeClass("activated");
    jQuery(active).addClass("activated");
    for (i in 0 until tracks.size) {
        var s = tracks[i];
        var item = listSong(s);
        if (item != null) {
            jQuery("#song-list").append(listSong(s));
        }
    }
}

fun analyzeAudio(audio:Any?, tag:Any?, callback:Any?) {
    var url = "qanalyze";
    jQuery.getJSON(url, {url: audio, tag: tag}, fun (data) {
        if (data.status == "done" || data.status == "error") {
            callback(data);
        } else {
            info(data.status + " - ready in about " + data.estimated_wait + " secs. ");
            setTimeout(fun () {
                analyzeAudio(audio, tag, callback);
            }, 5000);
        }
    });
}

// first see if it is in in S3 bucket, and if not, get the analysis from
// the labs server

fun noCache() {
    return {"noCache": now()}
}

fun fetchAnalysis(id:Any?) {
    var urlin = "/api/analysis/analyse/" + id;
    info("Fetching the analysis");

    jQuery.ajax(object:JQueryAjaxSettings{
        override val error = fun(jqXHR: JQueryXHR, textStatus: String, errorThrown: String) {
            info("Sorry, can't find info for that track: " + errorThrown)
        }
        override val success = fun (data: Any, textStatus: String, jqXHR: JQueryXHR) {
            gotTheAnalysis(data);
        }
    }.apply{
        url= urlin
        dataType= "json"
        type= "GET"
        crossDomain= true
        error
    });

    jQuery.ajax(object:JQueryAjaxSettings{
        override val error = fun(jqXHR: JQueryXHR, textStatus: String, errorThrown: String){
            info("Sorry, can't find info for that track: " + errorThrown)
        }
        override val success = fun(data: Any, textStatus: String, jqXHR: JQueryXHR) {
            if(data["url"] == undefined) {
                jQuery("#og-audio-source").remove();
            } else {
                jukeboxData.ogAudioURL = data["url"];
            }
        }
    }.apply{
        url= "/api/audio/jukebox/" + id + "/location"
        dataType= "json"
        type= "GET"
        crossDomain= true
    });
}

fun get_status(data:Any?):String {
    if (data.response.status.code == 0) {
        return data.response.track.status;
    } else {
        return "error";
    }
}

fun fetchSignature() {
    var url = "policy";
    jQuery.getJSON(url, {}, fun (data) {
        policy = data.policy;
        signature = data.signature;
        jQuery("#f-policy").`val`(data.policy);
        jQuery("#f-signature").`val`(data.signature);
        jQuery("#f-key").`val`(data.key);
    });
}

fun calculateDim(numTiles:Int, totalWidth:Double, totalHeight:Double):Double {
    var area = totalWidth * totalHeight;
    var tArea = area / (1.2 * numTiles);
    var dim = floor(sqrt(tArea));
    return dim;
}


var timbreWeight = 1
var pitchWeight = 10
var loudStartWeight = 1
var loudMaxWeight = 1
var durationWeight = 100
var confidenceWeight = 1

fun get_seg_distances(seg1:Any?, seg2:Any?):Number {
    var timbre = seg_distance(seg1, seg2, "timbre", true);
    var pitch = seg_distance(seg1, seg2, "pitches");
    var sloudStart = abs(seg1.loudness_start - seg2.loudness_start);
    var sloudMax = abs(seg1.loudness_max - seg2.loudness_max);
    var duration = abs(seg1.duration - seg2.duration);
    var confidence = abs(seg1.confidence - seg2.confidence);
    var distance = timbre * timbreWeight + pitch * pitchWeight +
            sloudStart * loudStartWeight + sloudMax * loudMaxWeight +
            duration * durationWeight + confidence * confidenceWeight;
    return distance;
}

fun dynamicCalculateNearestNeighbors(type:Any?):Int {
    var count = 0;
    var targetBranchCount = track.analysis[type].length / 6;

    precalculateNearestNeighbors(type, jukeboxData.maxBranches, jukeboxData.maxBranchThreshold);

    for (var threshold = 10; threshold < jukeboxData.maxBranchThreshold; threshold += 5) {
        count = collectNearestNeighbors(type, threshold);
        if (count >= targetBranchCount) {
            break;
        }
    }
    jukeboxData.currentThreshold = threshold;
    jukeboxData.computedThreshold = threshold;
    postProcessNearestNeighbors(type);
    return count;
}

fun postProcessNearestNeighbors(type:Any?) {
    removeDeletedEdges();

    if (jukeboxData.addLastEdge) {
        if (longestBackwardBranch(type) < 50) {
            insertBestBackwardBranch(type, jukeboxData.currentThreshold, 65);
        } else {
            insertBestBackwardBranch(type, jukeboxData.currentThreshold, 55);
        }
    }
    calculateReachability(type);
    jukeboxData.lastBranchPoint = findBestLastBeat(type);
    filterOutBadBranches(type, jukeboxData.lastBranchPoint);
    if (jukeboxData.removeSequentialBranches) {
        filterOutSequentialBranches(type);
    }
    setTunedURL();
}

fun removeDeletedEdges() {
    for (i in 0 until jukeboxData.deletedEdges.size) {
        var edgeID = jukeboxData.deletedEdges[i];
        if (edgeID in jukeboxData.allEdges) {
            var edge = jukeboxData.allEdges[edgeID];
            deleteEdge(edge);
        }
    }
    jukeboxData.deletedEdges = mutableListOf<Any?>();
}

fun getAllDeletedEdgeIDs():List<Any> {
    var results = mutableListOf<Any?>();
    for (i in 0 until jukeboxData.allEdges.size) {
        var edge = jukeboxData.allEdges[i];
        if (edge.deleted) {
            results.add(edge.id);
        }
    }
    return results;
}

fun getDeletedEdgeString():String {
    var ids = getAllDeletedEdgeIDs();
    if (ids.size > 0) {
        return "&d=" + ids.join(',');
    } else {
        return "";
    }
}

fun calculateNearestNeighbors(type:Any?, threshold:Any?):Int {
    precalculateNearestNeighbors(type, jukeboxData.maxBranches, jukeboxData.maxBranchThreshold);
    val count = collectNearestNeighbors(type, threshold);
    postProcessNearestNeighbors(type, threshold);
    return count;
}

fun resetTuning() {
    undeleteAllEdges();

    jukeboxData.addLastEdge = true;
    jukeboxData.justBackwards = false;
    jukeboxData.justLongBranches = false;
    jukeboxData.removeSequentialBranches = false;
    jukeboxData.currentThreshold = jukeboxData.computedThreshold;
    jukeboxData.minRandomBranchChance = defaultMinRandomBranchChance;
    jukeboxData.maxRandomBranchChance = defaultMaxRandomBranchChance;
    jukeboxData.randomBranchChanceDelta = defaultRandomBranchChanceDelta,

    jukeboxData.minRandomBranchChance = defaultMinRandomBranchChance;
    jukeboxData.maxRandomBranchChance = defaultMaxRandomBranchChance;
    jukeboxData.randomBranchChanceDelta = defaultRandomBranchChanceDelta;
    jukeboxData.audioURL = null;
    jukeboxData.disableKeys = false;

    drawVisualization();
}

fun undeleteAllEdges() {
    jukeboxData.deletedEdgeCount = 0;
    for (i in 0 until jukeboxData.allEdges.size) {
        var edge = jukeboxData.allEdges[i];
        if (edge.deleted) {
            edge.deleted = false;
        }
    }
}

fun setTunedURL() {
    if (track != null) {
        var edges = getDeletedEdgeString();
        var addBranchParams = false;
        var lb = "";

        if (!jukeboxData.addLastEdge) {
            lb = "&lb=0";
        }

        var p = "?id=" + track.info.id + edges + lb;

        if (jukeboxData.justBackwards) {
            p += "&jb=1"
        }

        if (jukeboxData.justLongBranches) {
            p += "&lg=1"
        }

        if (jukeboxData.removeSequentialBranches) {
            p += "&sq=0"
        }

        if (jukeboxData.currentThreshold !== jukeboxData.computedThreshold) {
            p += "&thresh=" + jukeboxData.currentThreshold;
        }

        if (jukeboxData.audioURL !== null) {
            p += "&audio=" + encodeURIComponent(jukeboxData.audioURL);
        }

        if (jukeboxData.minRandomBranchChance !== defaultMinRandomBranchChance) {
            addBranchParams = true;
        }
        if (jukeboxData.maxRandomBranchChance != defaultMaxRandomBranchChance) {
            addBranchParams = true;
        }

        if (jukeboxData.randomBranchChanceDelta != defaultRandomBranchChanceDelta) {
            addBranchParams = true;
        }

        if (addBranchParams) {
            p += "&bp=" + arrayOf(
                round(map_value_to_percent(jukeboxData.minRandomBranchChance, 0, 1)),
                round(map_value_to_percent(jukeboxData.maxRandomBranchChance, 0, 1)),
                round(map_value_to_percent(jukeboxData.randomBranchChanceDelta,
                        minRandomBranchChanceDelta, maxRandomBranchChanceDelta))).join(',')
        }

        if (jukeboxData.disableKeys) {
            p += "&nokeys=1"
        }

        history.replaceState({}, document.title, p);
        tweetSetup(track);
    }
}

fun now():Number {
    return Date().getTime()
}


// we want to find the best, long backwards branch
// and ensure that it is included in the graph to
// avoid short branching songs like:
// http://labs.echonest.com/Uploader/index.html?trid=TRVHPII13AFF43D495

fun longestBackwardBranch(type:Any?):Double {
    var longest = 0
    var quanta = track.analysis[type];
    for (i in 0 until quanta.length) {
        var q = quanta[i];
        for (j in 0 until q.neighbors.length) {
            var neighbor = q.neighbors[j];
            var which = neighbor.dest.which;
            var delta = i - which;
            if (delta > longest) {
                longest = delta;
            }
        }
    }
    var lbb:Double = longest * 100 / (quanta.length as Double)
    return lbb;
}

fun insertBestBackwardBranch(type:Any?, threshold:Any?, maxThreshold:Any?) {
    var found = false;
    var branches = mutableListOf<Any?>();
    var quanta = track.analysis[type];
    for (i in 0 until quanta.length) {
        var q = quanta[i];
        for (j in 0 until q.all_neighbors.length) {
            var neighbor = q.all_neighbors[j];

            if (neighbor.deleted) {
                continue;
            }

            var which = neighbor.dest.which;
            var thresh = neighbor.distance;
            var delta = i - which;
            if (delta > 0 && thresh < maxThreshold) {
                var percent = delta * 100 / quanta.length;
                var edge = [percent, i, which, q, neighbor]
                branches.add(edge);
            }
        }
    }

    if (branches.size == 0) {
        return;
    }

    branches.sort(
            fun (a, b) {
                return a[0] - b[0];
            }
    )
    branches.reverse();
    var best = branches[0];
    var bestQ = best[3];
    var bestNeighbor = best[4];
    var bestThreshold = bestNeighbor.distance;
    if (bestThreshold > threshold) {
        bestQ.neighbors.add(bestNeighbor);
        // console.log('added bbb from', bestQ.which, 'to', bestNeighbor.dest.which, 'thresh', bestThreshold);
    } else {
        // console.log('bbb is already in from', bestQ.which, 'to', bestNeighbor.dest.which, 'thresh', bestThreshold);
    }
}

fun calculateReachability(type:Any?) {
    var maxIter = 1000;
    var iter = 0;
    var quanta = track.analysis[type];

    for (qi in 0 until quanta.length) {
        var q = quanta[qi];
        q.reach = quanta.length - q.which;
    }

    for (iter in 0 until maxIter) {
        var changeCount = 0;
        for (qi in 0 until quanta.length) {
        var q = quanta[qi];
        var changed = false;

        for (i in 0 until q.neighbors.length) {
            var q2 = q.neighbors[i].dest;
            if (q2.reach > q.reach) {
                q.reach = q2.reach;
                changed = true;
            }
        }

        if (qi < quanta.length - 1) {
            var q2 = quanta[qi + 1];
            if (q2.reach > q.reach) {
                q.reach = q2.reach;
                changed = true;
            }
        }

        if (changed) {
            changeCount++;
            for (j in 0 until q.which) {
                var q2 = quanta[j];
                if (q2.reach < q.reach) {
                    q2.reach = q.reach;
                }
            }
        }
    }
        if (changeCount == 0) {
            break;
        }
    }

    if (false) {
        for (qi in 0 until quanta.length) {
            var q = quanta[qi];
            console.log(q.which, q.reach, round(q.reach * 100 / quanta.length));
        }
    }
    // console.log('reachability map converged after ' + iter + ' iterations. total ' + quanta.length);
}

fun map_percent_to_range(percent:Double, min:Double, max:Double) {
    val percent2 = clamp(percent, 0, 100)
    return (max - min) * percent / 100. + min;
}

fun map_value_to_percent(value:Any?, min:Any?, max:Any?) {
    val value2 = clamp(value, min, max)
    return 100 * (value - min) / (max - min);
}

fun clamp(value:Double, min:Double, max:Double):Double {
    return if(value < min) min else if(value > max) max else value
}

fun findBestLastBeat(type:Any?):Int {
    var reachThreshold = 50;
    var quanta = track.analysis[type];
    var longest = 0;
    var longestReach = 0;
    for (var i = quanta.length - 1; i >= 0; i--) {
        var q = quanta[i];
        //var reach = q.reach * 100 / quanta.length;
        var distanceToEnd = quanta.length - i;

        // if q is the last quanta, then we can never go past it
        // which limits our reach

        var reach = (q.reach - distanceToEnd) * 100 / quanta.length;

        if (reach > longestReach && q.neighbors.length > 0) {
            longestReach = reach;
            longest = i;
            if (reach >= reachThreshold) {
                break;
            }
        }
    }
    // console.log('NBest last beat is', longest, 'reach', longestReach, reach);

    jukeboxData.totalBeats = quanta.length;
    jukeboxData.longestReach = longestReach;
    return longest
}

fun filterOutBadBranches(type:Any?, lastIndex:Int) {
    var quanta = track.analysis[type];
    for (i in 0 until lastIndex) {
        var q = quanta[i];
        var newList = mutableListOf<Any?>();
        for (j in 0 until q.neighbors.length) {
            var neighbor = q.neighbors[j];
            if (neighbor.dest.which < lastIndex) {
                newList.add(neighbor);
            } else {
                // console.log('filtered out arc from', q.which, 'to', neighbor.dest.which);
            }
        }
        q.neighbors = newList;
    }
}

fun hasSequentialBranch(q:Any?, neighbor:Any?):Boolean {
    if (q.which == jukeboxData.lastBranchPoint) {
        return false;
    }

    var qp = q.prev;
    if (qp != null) {
        var distance = q.which - neighbor.dest.which;
        for (i in 0 until qp.neighbors.length) {
            var odistance = qp.which - qp.neighbors[i].dest.which;
            if (distance == odistance) {
                return true;
            }
        }
    }
    return false;
}

fun filterOutSequentialBranches(type:Any?) {
    var quanta = track.analysis[type];
    for (var i = quanta.length - 1; i >= 1; i--) {
        var q = quanta[i];
        var newList = mutableListOf<Any?>();

        for (j in 0 until q.neighbors.length) {
            var neighbor = q.neighbors[j];
            if (hasSequentialBranch(q, neighbor)) {
                // skip it
            } else {
                newList.add(neighbor);
            }
        }
        q.neighbors = newList;
    }
}

fun calculateNearestNeighborsForQuantum(type:Any?, maxNeighbors:Any?, maxThreshold:Any?, q1:Any?) {
    var edges = mutableListOf<Any?>();
    var id = 0;
    for (i in 0 until track.analysis[type].length) {

        if (i == q1.which) {
            continue;
        }

        var q2 = track.analysis[type][i];
        var sum = 0;
        for (j in 0 until q1.overlappingSegments.length) {
            var seg1 = q1.overlappingSegments[j];
            var distance = 100;
            if (j < q2.overlappingSegments.length) {
                var seg2 = q2.overlappingSegments[j];
                // some segments can overlap many quantums,
                // we don't want this self segue, so give them a
                // high distance
                if (seg1.which == seg2.which) {
                    distance = 100
                } else {
                    distance = get_seg_distances(seg1, seg2);
                }
            }
            sum += distance;
        }
        var pdistance = q1.indexInParent == q2.indexInParent ? 0 : 100;
        var totalDistance = sum / q1.overlappingSegments.length + pdistance;
        if (totalDistance < maxThreshold) {
            var edge = {
                id: id,
                src: q1,
                dest: q2,
                distance: totalDistance,
                curve: null,
                deleted: false
            };
            edges.add(edge);
            id++;
        }
    }

    edges.sort(
            fun (a, b) {
                if (a.distance > b.distance) {
                    return 1;
                } else if (b.distance > a.distance) {
                    return -1;
                } else {
                    return 0;
                }
            }
    );

    q1.all_neighbors = mutableListOf<Any?>();
    for (i = 0; i < maxNeighbors && i < edges.size; i++) {
        var edge = edges[i];
        q1.all_neighbors.add(edge);

        edge.id = jukeboxData.allEdges.size;
        jukeboxData.allEdges.add(edge);
    }
}

fun precalculateNearestNeighbors(type:Any?, maxNeighbors:Any?, maxThreshold:Any?) {
    // skip if this is already done
    if ("all_neighbors" in track.analysis[type][0]) {
        return;
    }
    jukeboxData.allEdges = mutableListOf<Any?>();
    for (qi in 0 until track.analysis[type].length) {
        var q1 = track.analysis[type][qi];
        calculateNearestNeighborsForQuantum(type, maxNeighbors, maxThreshold, q1);
    }
}

fun collectNearestNeighbors(type:Any?, maxThreshold:Any?):Int {
    var branchingCount = 0;
    for (qi in 0 until track.analysis[type].length) {
        var q1 = track.analysis[type][qi];
        q1.neighbors = extractNearestNeighbors(q1, maxThreshold);
        if (q1.neighbors.length > 0) {
            branchingCount += 1;
        }
    }
    return branchingCount;
}

fun extractNearestNeighbors(q:Any?, maxThreshold:Any?):List<Any?> {
    var neighbors = mutableListOf<Any?>();

    for (i in 0 until q.all_neighbors.length) {
        var neighbor = q.all_neighbors[i];

        if (neighbor.deleted) {
            continue;
        }

        if (jukeboxData.justBackwards && neighbor.dest.which > q.which) {
            continue;
        }

        if (jukeboxData.justLongBranches && abs(neighbor.dest.which - q.which) < jukeboxData.minLongBranch) {
            continue;
        }

        var distance = neighbor.distance;
        if (distance <= maxThreshold) {
            neighbors.add(neighbor);
        }
    }
    return neighbors;
}

fun seg_distance(seg1:Any?, seg2:Any?, field:Any?, weighted:Boolean=false):Number {
    return if (weighted) {
        weighted_euclidean_distance(seg1[field], seg2[field]);
    } else {
        euclidean_distance(seg1[field], seg2[field]);
    }
}

fun calcBranchInfo(type:Any?) {
    var histogram = {}
    var total = 0;
    for (qi in 0 until track.analysis[type].length) {
        var q = track.analysis[type][qi];
        for (i in 0 until q.neighbors.length) {
            var neighbor = q.neighbors[i];
            var distance = neighbor.distance;
            var bucket = round(distance / 10);
            if (!(bucket in histogram)) {
                histogram[bucket] = 0;
            }
            histogram[bucket] += 1;
            total += 1;
        }
    }
    console.log(histogram);
    console.log("total branches", total);
}

fun euclidean_distance(v1:Any?, v2:Any?):Double {
    var sum = 0;

    for (i in 0 until v1.size) {
        var delta = v2[i] - v1[i];
        sum += delta * delta;
    }
    return sqrt(sum);
}

fun weighted_euclidean_distance(v1:Any?, v2:Any?):Number {
    var sum = 0;

    //for (i in 0 until 4) {
    for (i in 0 until v1.size) {
        var delta = v2[i] - v1[i];
        //var weight = 1.0 / ( i + 1.0);
        var weight = 1.0;
        sum += delta * delta * weight;
    }
    return sqrt(sum);
}

fun redrawTiles() {
    jukeboxData.tiles.forEach { tile ->
        var newWidth = round((minTileWidth + tile.playCount * growthPerPlay) * curGrowFactor);
        if (newWidth < 1) {
            newWidth = 1;
        }
        tile.rect.attr("width", newWidth);
    }
}

fun highlightCurves(tile:Any?, enable:Any?, didJump:Any?) {
    for (i in 0 until tile.q.neighbors.length) {
        var curve = tile.q.neighbors[i].curve;
        highlightCurve(curve, enable, didJump);
        if (driver.isRunning()) {
            break; // just highlight the first one
        }
    }
}

fun getQuantumColor(q:Any?):String {
    if (isSegment(q)) {
        return getSegmentColor(q);
    } else {
        q = getQuantumSegment(q);
        if (q != null) {
            return getSegmentColor(q);
        } else {
            return "#000";
        }
    }
}

fun getQuantumSegment(q:Any?) {
    return q.oseg;
}

fun isSegment(q:Any?):Boolean {
    return "timbre" in q;
}

fun getBranchColor(q:Any?) {
    if (q.neighbors.length == 0) {
        return to_rgb(0, 0, 0);
    } else {
        var red = q.neighbors.length / jukeboxData.maxBranches;
        return to_rgb(red, 0, (1. - red));
    }
}

fun createNewTile(which:Any?, q:Any?, height:Any?, width:Any?):TilePrototype = TilePrototype(which, q, height, width)

fun createTilePanel(which:Any?) {
    removeAllTiles();
    jukeboxData.tiles = createTiles(which);
}

fun normalizeColor() {
    val cmin = mutableListOf(100, 100, 100)
    val cmax = mutableListOf(-100, -100, -100)

    var qlist = track.analysis.segments;
    for (i in 0 until qlist.length) {
        for (j in 0 until 3) {
            var t = qlist[i].timbre[j + 1];

            if (t < cmin[j]) {
                cmin[j] = t;
            }
            if (t > cmax[j]) {
                cmax[j] = t;
            }
        }
    }
}

fun getSegmentColor(seg:Any?):String {
    var results = mutableListOf<Any?>();
    for (i in 0 until 3) {
        var t = seg.timbre[i + 1];
        var norm = (t - cmin[i]) / (cmax[i] - cmin[i]);
        results[i] = norm * 255;
        results[i] = norm;
    }
    return to_rgb(results[1], results[2], results[0]);
    //return to_rgb(results[0], results[1], results[2]);
}

fun convert(value:Double):String {
    var integer = round(value);
    var str = Number(integer).toString(16);
    return if(str.length == 1) "0" + str else str
};

fun to_rgb(r:Double, g:Double, b:Double):String {
    return "#" + convert(r * 255) + convert(g * 255) + convert(b * 255);
}

fun removeAllTiles() {
    for (i in 0 until jukeboxData.tiles.size) {
        jukeboxData.tiles[i].rect.remove();
    }
    jukeboxData.tiles = mutableListOf<Any?>();
}

fun deleteEdge(edge:Any?) {
    if (!edge.deleted) {
        jukeboxData.deletedEdgeCount++;
        edge.deleted = true;
        if (edge.curve) {
            edge.curve.remove();
            edge.curve = null;
        }
        for (j in 0 until edge.src.neighbors.length) {
            var otherEdge = edge.src.neighbors[j];
            if (edge == otherEdge) {
                edge.src.neighbors.splice(j, 1);
                break;
            }
        }
    }
}

fun keydown(evt:JQueryKeyEventObject) {
    if (!jQuery("#hero").is(":visible") || jQuery("#controls").is(":visible") || jukeboxData.disableKeys) {
        return;
    }

    if (evt.which == 39) {  // right arrow
        var inc = driver.getIncr();
        driver.setIncr(inc + 1);
        evt.preventDefault();
    }

    if (evt.which == 8 || evt.which == 46) {     // backspace / delete
        evt.preventDefault();
        if (jukeboxData.selectedCurve) {
            deleteEdge(jukeboxData.selectedCurve.edge);
            jukeboxData.selectedCurve = null;
            drawVisualization();
        }
    }

    if (evt.which == 37) {  // left arrow
        evt.preventDefault();
        var inc = driver.getIncr();
        driver.setIncr(inc - 1);
    }

    if (evt.which == 38) {  // up arrow
        driver.setIncr(1);
        evt.preventDefault();
    }

    if (evt.which == 40) {  // down arrow
        driver.setIncr(0);
        evt.preventDefault();
    }


    if (evt.which == 17) {
        controlled = true;
    }

    if (evt.which == 72) {
        jukeboxData.infiniteMode = !jukeboxData.infiniteMode;
        if (jukeboxData.infiniteMode) {
            info("Infinite Mode enabled");
            ga_track("main", "infinite-mode", "");
        } else {
            info("Bringing it on home");
            ga_track("main", "home", "");
        }
    }

    if (evt.which == 16) {
        shifted = true;
    }

    if (evt.which == 32) {
        evt.preventDefault();
        if (driver.isRunning()) {
            driver.stop();
            ga_track("main", "key-stop", "");
        } else {
            driver.start();
            ga_track("main", "key-start", "");
        }
    }

}

fun isDigit(key:Int):Boolean {
    return key >= 48 && key <= 57;
}

fun keyup(evt:JQueryKeyEventObject) {
    if (evt.which == 17) {
        controlled = false;
    }
    if (evt.which == 16) {
        shifted = false;
    }
}

fun searchForTrack() {
    console.log("search for a track");
    var q = jQuery("#search-text").val();
    console.log(q);

    if (q.length > 0) {
        var url = "search";
        jQuery.getJSON(url, {q: q, results: 30}, fun (data) {
            console.log(data);
            for (i in 0 until data.length) {
                data[i].id = data[i].id;
            }
            listTracks("#search-list", data);
        });
    }
}

fun getShareURL(callback:(String)->Unit) {
    var q = document.URL.split('?')[1];

    jQuery.ajaxBuilder(
        url= "/api/site/shrink",
        dataType= "json",
        type= "POST",
        data= if(q == null) "service=jukebox" else "service=jukebox&" + q,
        success= fun (data) {
            return callback(data["id"]);
        },
        error= fun (xhr, textStatus, error) {
            console.log("Error: " + error);
            return "NOT-VALID";
        }
    )
}

fun checkIfStarred() {
    getShareURL(fun (id) {
        jQuery.ajax(object:JQueryAjaxSettings{}.apply{
            url= "/api/profile/me"
            dataType= "json"
            type= "GET"
            success= fun (data) {
                var stars = data["stars"];
                for (i in 0 until stars.length) {
                    if (stars[i] == id) {
                        jQuery("#star").text("Unstar");
                        break;
                    }
                }
            }
            error= fun (xhr, textStatus, error) {
                console.log("Could not retrieve stars: " + error)
            }
        });
    });
}

fun init() {
    document.ondblclick = fun (event):Boolean {
        event.preventDefault();
        event.stopPropagation();
        return false;
    };

    jQuery(document).keydown(::keydown);
    jQuery(document).keyup(::keyup);

    paper = Raphael("tiles", W, H);

    jQuery("#error").hide();


    jQuery("#load").click(
            fun () {
                ga_track("main", "load", "");
                if (!uploadingAllowed) {
                    alert("Sorry, uploading is temporarily disabled, while we are under heavy load");
                } else {
                    location.href = "loader.html";
                }
            }
    );

    jQuery("#go").click(
            fun () {
                if (driver.isRunning()) {
                    driver.stop();
                    ga_track("main", "stop", track.info.id);
                } else {
                    driver.start();
                    ga_track("main", "start", track.info.id);
                }
            }
    );

    jQuery("#search").click(::searchForTrack);
    jQuery("#search-text").keyup(fun (e) {
        if (e.keyCode == 13) {
            searchForTrack();
        }
    });

    jQuery("#new").click(
            fun () {
                if (driver) {
                    driver.stop();
                }
                setDisplayMode(false);
                ga_track("main", "new", "");
            }
    );

    jQuery("#tune").click(
            fun () {
                var controls = jQuery("#controls");
                if (jukeboxData.tuningOpen)
                    controls.dialog("close");
                else
                    controls.dialog("open");
                jukeboxData.tuningOpen = !jukeboxData.tuningOpen;
                ga_track("main", "tune", "");
            }
    );

    jQuery("#star").click(
            fun () {
                getShareURL(fun (shortID) {
                    jQuery.ajax(object:JQueryAjaxSettings{}.apply{
                        url= "/api/profile/stars/" + shortID
                        type=  if(jQuery("#star").text() == "Star") "PUT" else "DELETE"
                        headers= {
                            "X-XSRF-TOKEN": document.cookie.substring(document.cookie.indexOf("XSRF-TOKEN")).split(";")[0].split("=").slice(1).join("=")
                        }
                        success= fun (data) {
                            if (jQuery("#star").text() == "Star") {
                                jQuery("#info").text("Successfully starred!");
                                jQuery("#star").text("Unstar");
                            } else {
                                jQuery("#info").text("Successfully unstarred!");
                                jQuery("#star").text("Star");
                            }
                        }
                        error= fun (xhr, textStatus, error) {
                            if (error == "Unauthorized")
                            jQuery("#info").text("An error occurred while starring: You're not logged in!");
                            else
                            jQuery("#info").text("An error occurred while starring: " + error + "!");
                        }
                    });
                });
            }
    );

    jQuery("#short-url").click(
            fun () {
                getShareURL(fun (id) {
                    window.prompt("Copy the URL below and press 'Enter' to automatically close this prompt",
                            window.location.origin + "/api/site/expand/" + id + "/redirect")
                })
            }
    );

    jQuery("#og-audio-source").click(
            fun () {
                location.href = jukeboxData.ogAudioURL;
            }
    );

    jQuery("#canonize").click(
            fun () {
                location.href = document.URL.replace("jukebox_go", "canonizer_go");
            }
    );

    jQuery("#controls").attr("visibility", "visible");
    jQuery("#controls").dialog(object:DialogOptions{}.apply
            {
                autoOpen= false
                title= "Fine tune your endless song"
                width= 350
                position= arrayOf(4, 4)
                resizable= false
            }
    );

    jQuery("#reset-edges").click(
            fun () {
                resetTuning();
                ga_track("main", "reset", "");
            }
    );

    jQuery("#close-tune").click(
            fun() {
                var controls = jQuery("#controls");
                controls.dialog("close");
                jukeboxData.tuningOpen = false;
            }
    );

    jQuery("#last-branch").change(
            fun (event) {
                if (event.originalEvent) {
                    jukeboxData.addLastEdge = jQuery("#last-branch").is(":checked");
                    drawVisualization();
                }
            }
    );

    jQuery("#reverse-branch").change(
            fun (event) {
                if (event.originalEvent) {
                    jukeboxData.justBackwards = jQuery("#reverse-branch").is(":checked");
                    drawVisualization();
                }
            }
    );

    jQuery("#long-branch").change(
            fun (event) {
                if (event.originalEvent) {
                    jukeboxData.justLongBranches = jQuery("#long-branch").is(":checked");
                    drawVisualization();
                }
            }
    );

    jQuery("#sequential-branch").change(
            fun (event) {
                if (event.originalEvent) {
                    jukeboxData.removeSequentialBranches = jQuery("#sequential-branch").is(":checked");
                    drawVisualization();
                }
            }
    );

    jQuery("#threshold-slider").slider(object: SliderOptions{}.apply{
        max= 80
        min= 2
        step= 1
        value= 30
        change= fun (event, ui) {
            if (event.originalEvent) {
                jukeboxData.currentThreshold = ui.value;
                drawVisualization();
            }
        }

        slide= fun (event, ui) {
            if (event.originalEvent) {
                jukeboxData.currentThreshold = ui.value;
            }
        }

    }
    );

    jQuery("#probability-slider").slider(object: SliderOptions{}.apply{
        max= 100
        min= 0
        range= true
        step= 1
        values= arrayOf(
            round(defaultMinRandomBranchChance * 100),
            round(defaultMaxRandomBranchChance * 100)
        )
        change= fun (event, ui) {
            if (event.originalEvent) {
                jukeboxData.minRandomBranchChance = ui.values[0] / 100.;
                jukeboxData.maxRandomBranchChance = ui.values[1] / 100.;
                setTunedURL();
            }
        }

        slide= fun (event, ui) {
            if (event.originalEvent) {
                jukeboxData.minRandomBranchChance = ui.values[0] / 100.;
                jukeboxData.maxRandomBranchChance = ui.values[1] / 100.;
            }
        }
    }
    );

    jQuery("#probability-ramp-slider").slider(object: SliderOptions{}.apply{
        max= 100
        min= 0
        step= 2
        value= 30
        change= fun (event, ui) {
            if (event.originalEvent) {
                jukeboxData.randomBranchChanceDelta =
                        map_percent_to_range(ui.value, minRandomBranchChanceDelta, maxRandomBranchChanceDelta)
                setTunedURL();
            }
        }

        slide= fun (event, ui) {
            if (event.originalEvent) {
                jukeboxData.randomBranchChanceDelta =
                        map_percent_to_range(ui.value, minRandomBranchChanceDelta, maxRandomBranchChanceDelta)
            }
        }
    }
    );

    jQuery("#audio-url").keypress(fun (event:JQueryKeyEventObject) {
        var keycode = if(event.keyCode != 0) event.keyCode else event.which
        if (keycode == 13) {
            jukeboxData.audioURL = event.target.value;
            setTunedURL();
            window.location.reload(true);
        }
    });

    jQuery("#audio-upload").change(fun () {
        jQuery.ajax(object:JQueryAjaxSettings{}.apply{
            url= "/api/audio/upload"
            type= "POST"
            data= FormData(jQuery("#audio-upload-form").get(0))
            processData= false
            contentType= false
            headers= {
                "X-XSRF-TOKEN": document.cookie.substring(document.cookie.indexOf("XSRF-TOKEN")).split(";")[0].split("=").slice(1).join("=")
            }
            xhr= fun () {
                var xhr = XMLHttpRequest();
                xhr.upload.addEventListener("progress", fun (evt) {
                    if (evt.lengthComputable) {
                        var percentComplete = evt.loaded / evt.total;
                        percentComplete = percentComplete * 100;
                        jQuery("#audio-progress").text(percentComplete + '%');
                        jQuery("#audio-progress").css("width", percentComplete + '%');
                    }
                }, false);
                return xhr;
            }
            success= fun (data) {
                jukeboxData.audioURL = "upl:" + data["id"];
                setTunedURL();
                window.location.reload(true);
            }
            error= fun (xhr, textStatus, error) {
            console.log("Error upon attempting to upload: " + error);
        }
        });
    });

    jQuery("#disable-keys").change(
            fun (event) {
                if (event.originalEvent) {
                    jukeboxData.disableKeys = jQuery("#disable-keys").`is`(":checked")
                    setTunedURL();
                }
            }
    );

    jQuery("#volume-slider").slider(object: SliderOptions{}.apply{
        min= 0
        max= 100
        value= 50
        range= "min"
        slide= fun(event, ui) {
            jQuery("#volume").text(ui.value);
            player.audioGain.gain.value = ui.value / 100;
            //setVolume(ui.value / 100);
        }
    });

    jukeboxData.minRandomBranchChance = defaultMinRandomBranchChance;
    jukeboxData.maxRandomBranchChance = defaultMaxRandomBranchChance;
    jukeboxData.randomBranchChanceDelta = defaultRandomBranchChanceDelta;


    var context = getAudioContext();
    if (context == null) {
        error("Sorry, this app needs advanced web audio. Your browser doesn't"
                + " support it. Try the latest version of Chrome, Firefox, or Safari");

        hideAll();

    } else {
        remixer = JRemixer(context, jQuery)
        player = remixer.getPlayer();
        processParams();
        checkIfStarred();
    }
}

fun getAudioContext():AudioContext? {
    return AudioContext.prototype
}

fun secondsToTime(secsIn:Double):String {
    var secs = floor(secsIn);
    var hours = floor(secs / 3600);
    secs -= hours * 3600;
    var mins = floor(secs / 60);
    secs -= mins * 60;

    //these aren't really Strings, but it's fine because they're auto-cast when used anyway
    val hoursString = if (hours < 10) "0" + hours else hours
    val minsString = if (mins < 10) "0" + mins else mins
    val secsString = if (secs < 10) "0" + secs else secs
    return  "$hoursString:$minsString:$secsString"
}

fun windowHidden():Boolean {
    return document.body?.hidden ?: true
}

fun processParams() {
    var params = mutableMapOf<String, String>()
    var q = URL.toString().split('?')[1]
    if (q.isNotEmpty()) {
        val q2 = q.split('&')
        for (i in 0 until q2.size) {
            var pv = q2[i].split('=');
            var p = pv[0];
            var v = pv[1];
            params[p] = v;
        }
    }

    if ("id" in params) {
        var id = params["id"];
        jukeboxData.trackID = id;

        var thresh = 0;
        if ("thresh" in params) {
            jukeboxData.currentThreshold = parseInt(params["thresh"]);
        }
        if ("audio" in params) {
            jukeboxData.audioURL = decodeURIComponent(params["audio"]);
        }
        if ("d" in params) {
            var df = params['d'].split(',');
            for (i in 0 until df.length) {
                var di = parseInt(df[i]);
                jukeboxData.deletedEdges.add(di);
            }
        }
        if ("lb" in params) {
            if (params["lb"] == "0") {
                jukeboxData.addLastEdge = true;
            }
        }

        if ("jb" in params) {
            if (params["jb"] == "1") {
                jukeboxData.justBackwards = true;
            }
        }

        if ("lg" in params) {
            if (params["lg"] == "1") {
                jukeboxData.justLongBranches = true;
            }
        }

        if ("sq" in params) {
            if (params["sq"] == "0") {
                jukeboxData.removeSequentialBranches = true;
            }
        }

        if ("nokeys" in params) {
            if (params["nokeys"] == "1") {
                jukeboxData.disableKeys = true
            }
        }

        if ("bp" in params) {
            var bp = params["bp"];
            var fields = bp.split(',');
            if (fields.length == 3) {
                var minRange = parseInt(fields[0]);
                var maxRange = parseInt(fields[1]);
                var delta = parseInt(fields[2]);

                jukeboxData.minRandomBranchChance = map_percent_to_range(minRange, 0, 1);
                jukeboxData.maxRandomBranchChance = map_percent_to_range(maxRange, 0, 1);
                jukeboxData.randomBranchChanceDelta =
                        map_percent_to_range(delta, minRandomBranchChanceDelta, maxRandomBranchChanceDelta);

            }
        }
        setDisplayMode(true);
        fetchAnalysis(id);
    } else if ("key" in params) {
        var url = "http://" + params["bucket"] + '/' + urldecode(params["key"]);
        info("analyzing audio");
        setDisplayMode(true);
        jQuery("#select-track").hide();
        analyzeAudio(url, "tag",
                fun (data) {
                    if (data.status == "done") {
                        showPlotPage(data.id);
                    } else {
                        info("Trouble analyzing that track " + data.message);
                    }
                }
        );
    }
    else {
        setDisplayMode(false);
    }
}

fun showPlotPage(id:Any?) {
    var url = location.protocol + "//" +
            location.host + location.pathname + "?id=" + id;
    location.href = url;
}

fun urldecode(str:Any?) {
    return decodeURIComponent((str + "").replace(/\+/g, "%20"));
}

fun isTuned(url:String):Boolean {
    return url.indexOf('&') > 0;
}
//TODO: fix social media & analytics stuff later

fun tweetSetup(t:Any?) {
    jQuery(".twitter-share-button").remove();
    var tweet = jQuery("<a>")
    .attr("href", "https://twitter.com/share")
            .attr("id", "tweet")
            .attr("class", "twitter-share-button")
            .attr("data-lang", "en")
            .attr("data-count", "none")
            .text("Tweet");

    jQuery("#tweet-span").prepend(tweet);
    if (t) {
        var tuned = "";
        if (isTuned(document.URL)) {
            tuned = "Tuned ";
        }
        tweet.attr("data-text", tuned + "#EternalJukebox of " + t.fixedTitle);
        tweet.attr("data-url", document.URL);
    }
    // twitter can be troublesome. If it is not there, don't bother loading it
    if ("twttr" in window) {
        twttr.widgets.load();
    }
}

fun ga_track(page:Any?, action:Any?, id:Any?) {
    //_gaq.add(["_trackEvent", page, action, id]);
}

//window.onload = init;
