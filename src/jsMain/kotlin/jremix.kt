import externaljs.jquery.JQueryStatic
import externaljs.typescript.AudioContext
import org.w3c.xhr.XMLHttpRequest
import kotlin.math.round
import kotlin.math.sqrt

class JRemixer(private val context: AudioContext, private val jquery:JQueryStatic) {

    fun remixTrackById (id:Any?, callback:(Int, track:Any?, Number)->Unit) {
        jquery.getJSON("api/info/" + id, fun(data:Any?) {
            remixer.remixTrack(data, callback)
        });
    }

    private fun fetchAudio(url:String) {
        var request = XMLHttpRequest()
        trace("fetchAudio " + url);
        track.buffer = null;
        request.open("GET", url, true);
        request.responseType = "arraybuffer";
        this.request = request;

        request.onload = fun(event) {
            trace("audio loaded")
            if (false) {
                track.buffer = context.createBuffer(request.response, false);
                track.status = "ok"
                callback(1, track, 100);
            } else {
                context.decodeAudioData(request.response,
                        fun(buffer) {      // completed fun
                            track.buffer = buffer;
                            track.status = "ok"
                            callback(1, track, 100);
                        },
                        fun(e) { // error fun
                            track.status = "error: loading audio"
                            callback(-1, track, 0);
                            console.log("audio error", e)
                        }
                );
            }
        };

        request.onerror = fun(e) {
            trace("error loading loaded")
            track.status = "error: loading audio"
            callback(-1, track, 0);
        };

        request.onprogress = fun(e) {
            var percent = round(e.loaded * 100  / e.total)
            callback(0, track, percent);
        };
        request.send();
    }

    private fun preprocessTrack(track:Any?) {
        trace("preprocessTrack")
        val types = arrayOf("sections", "bars", "beats", "tatums", "segments")

        for (i in types.indices) {
            var type = types[i];
            trace("preprocessTrack " + type)
            for (var j in track.analysis[type]) {
                var qlist = track.analysis[type];

                j = parseInt(j);

                var q = qlist[j];
                q.track = track;
                q.which = j;
                if (j > 0) {
                    q.prev = qlist[j-1];
                } else {
                    q.prev = null
                }

                if (j < qlist.length - 1) {
                    q.next = qlist[j+1];
                } else {
                    q.next = null
                }
            }
        }

        connectQuanta(track, "sections", "bars")
        connectQuanta(track, "bars", "beats")
        connectQuanta(track, "beats", "tatums")
        connectQuanta(track, "tatums", "segments")

        connectFirstOverlappingSegment(track, "bars")
        connectFirstOverlappingSegment(track, "beats")
        connectFirstOverlappingSegment(track, "tatums")

        connectAllOverlappingSegments(track, "bars")
        connectAllOverlappingSegments(track, "beats")
        connectAllOverlappingSegments(track, "tatums")


        filterSegments(track);
    }

    private fun filterSegments(track:Any?) {
        var threshold = .3;
        var fsegs = mutableListOf<Any?>()
        fsegs.add(track.analysis.segments[0])
        for (i in 1 until track.analysis.segments.length) {
            var seg = track.analysis.segments[i];
            var last = fsegs[fsegs.length - 1];
            if (isSimilar(seg, last) && seg.confidence < threshold) {
                fsegs[fsegs.length -1].duration += seg.duration;
            } else {
                fsegs.add(seg)
            }
        }
        track.analysis.fsegments = fsegs;
    }

    private fun isSimilar(seg1:Any?, seg2:Any?) {
        var threshold = 1;
        var distance = timbral_distance(seg1, seg2);
        return (distance < threshold);
    }

    private fun connectQuanta(track:Any?, parent:Any?, child:Any?) {
        var last = 0;
        var qparents = track.analysis[parent];
        var qchildren = track.analysis[child];

        for (i in qparents) {
            var qparent = qparents[i];
            qparent.children = mutableListOf<Any?>()

            for (j in last until qchildren.length) {
                var qchild = qchildren[j];
                if (qchild.start >= qparent.start
                        && qchild.start < qparent.start + qparent.duration) {
                    qchild.parent = qparent;
                    qchild.indexInParent = qparent.children.length;
                    qparent.children.push(qchild);
                    last = j;
                } else if (qchild.start > qparent.start) {
                    break;
                }
            }
        }
    }

    // connects a quanta with the first overlapping segment
    private fun connectFirstOverlappingSegment(track:Any?, quanta_name:Any?) {
        var last = 0;
        var quanta = track.analysis[quanta_name];
        var segs = track.analysis.segments;

        for (i in 0 until quanta.length) {
            var q = quanta[i];

            for (j in last until segs.length) {
                var qseg = segs[j];
                if (qseg.start >= q.start) {
                    q.oseg = qseg;
                    last = j;
                    break
                }
            }
        }
    }

    private fun connectAllOverlappingSegments(track:Any?, quanta_name:Any?) {
        var last = 0;
        var quanta = track.analysis[quanta_name];
        var segs = track.analysis.segments;

        for (i in 0 until quanta.length) {
            var q = quanta[i];
            q.overlappingSegments = mutableListOf<Any?>()

            for (j in last until segs.length) {
                var qseg = segs[j];
                // seg starts before quantum so no
                if ((qseg.start + qseg.duration) < q.start) {
                    continue;
                }
                // seg starts after quantum so no
                if (qseg.start > (q.start + q.duration)) {
                    break;
                }
                last = j;
                q.overlappingSegments.push(qseg);
            }
        }
    }

    fun remixTrack(track:Any?, callback:(Int, track:Any?, Number)->Unit) {
        preprocessTrack(track);
        fetchAudio(
            if(jukeboxData.audioURL == null) {
                "api/audio/jukebox/" + track.info.id
            } else {
                "api/audio/external?fallbackID=" + track.info.id + "&url=" + encodeURIComponent(jukeboxData.audioURL)
            }
        )
    }

    fun getPlayer () {
        var queueTime:Number = 0;
        var audioGain = context.createGain();
        var curAudioSource = null;
        var curQ = null;
        audioGain.gain.value = 0.5;
        audioGain.connect(context.destination);

        fun queuePlay(`when`:Number, q:Any?):Number {
            // console.log('qp', when, q);
            //audioGain.gain.value = 1;
            if (isAudioBuffer(q)) {
                var audioSource = context.createBufferSource();
                audioSource.buffer = q;
                audioSource.connect(audioGain);
                audioSource.start(`when`);
                return `when`
            } else if (jquery.isArray(q)) {
                for (i in q) {
                    `when` = queuePlay(`when`, q[i]);
                }
                return `when`
            } else if (isQuantum(q)) {
                var audioSource = context.createBufferSource();
                audioSource.buffer = q.track.buffer;
                audioSource.connect(audioGain);
                audioSource.start(`when`, q.start, q.duration);
                q.audioSource = audioSource;
                return `when` + q.duration
            } else {
                error("can't play " + q);
                return `when`
            }
        }

        fun playQuantum(`when`:Number, q:Any?) {
            var now = context.currentTime;
            var start = if(`when` == 0) now else `when`
            var next = start + q.duration;

            if (curQ && curQ.track === q.track && curQ.which + 1 == q.which) {
                // let it ride
            } else {
                var audioSource = context.createBufferSource();
                //audioGain.gain.value = 1;
                audioSource.buffer = q.track.buffer;
                audioSource.connect(audioGain);
                var duration = track.audio_summary.duration - q.start;
                audioSource.start(start, q.start, duration);
                if (curAudioSource) {
                    curAudioSource.stop(start);
                }
                curAudioSource = audioSource;
            }
            q.audioSource = curAudioSource;
            curQ = q;
            return next;
        }

        fun error(s:Any?) {
            console.log(s);
        }

        var player = {
            audioGain= audioGain

            fun play(`when`:Any?, q:Any?) {
                return playQuantum(`when`, q);
                //queuePlay(0, q);
            }

            fun playNow (q:Any?) {
                queuePlay(0, q);
            }

            fun addCallback (callback:Any?) {
            }

            fun queue (q:Any?) {
                var now = context.currentTime;
                if (now > queueTime) {
                    queueTime = now;
                }
                queueTime = queuePlay(queueTime, q);
            }

            fun queueRest (duration:Number) {
                queueTime += duration;
            }

            fun stop (q:Any?) {
                if (q === undefined) {
                    if (curAudioSource) {
                        curAudioSource.stop(0);
                        curAudioSource = null;
                    }
                    //audioGain.gain.value = 0;
                    //audioGain.disconnect();
                } else {
                    if ("audioSource" in q) {
                        if (q.audioSource !== null) {
                            q.audioSource.stop(0);
                        }
                    }
                }
                curQ = null;
            }

            fun curTime () {
                return context.currentTime;
            }
        };
        return player;
    }

    fun fetchSound (audioURL:Any?, callback:Any?) {
        var request = XMLHttpRequest();

        trace("fetchSound " + audioURL);
        request.open("GET", audioURL, true);
        request.responseType = "arraybuffer";
        this.request = request;

        request.onload = fun() {
            var buffer = context.createBuffer(request.response, false);
            callback(true, buffer);
        };

        request.onerror = fun(e) {
            callback(false, null);
        };
        request.send();
    }

}

fun isQuantum(a:List<String>):Boolean {
    return "start" in a && "duration" in a
}

fun isAudioBuffer(a:List<String>):Boolean {
    return "getChannelData" in a
}

fun trace(text:String) {
    if (false) {
        console.log(text);
    }
}

fun euclidean_distance(v1:Any?, v2:Any?):Number {
    var sum:Double = 0.0
    for (i in 0 until 3) {
        var delta = v2[i] - v1[i];
        sum += delta * delta;
    }
    return sqrt(sum)
}

fun timbral_distance(s1:Any?, s2:Any?):Number {
    return euclidean_distance(s1.timbre, s2.timbre);
}


fun clusterSegments(track:Any?, numClusters:Int, fieldName:String?, vecName:String?) {
    val vname:String = vecName ?: "timbre"
    val fname:String = fieldName ?: "cluster"
    var maxLoops = 1000;

    fun zeroArray(size:Int):Array<Number> {
        return Array<Number>(size){0}
    }

    fun reportClusteringStats() {
        var counts = zeroArray(numClusters);
        for (i in 0 until track.analysis.segments.length) {
            var cluster = track.analysis.segments[i][fname];
            counts[cluster]++;
        }
        //console.log('clustering stats');
        for (i in 0 until counts.size) {
            //console.log('clus', i, counts[i]);
        }
    }

    fun sumArray(v1:Array<Any?>, v2:Array<Any?>) {
        for (i in 0 until v1.size) {
            v1[i] += v2[i];
        }
        return v1;
    }

    fun divArray(v1:Array<Number>, scalar:Number) {
        for (i in 0 until v1.size) {
            v1[i] /= scalar
        }
        return v1;
    }
    fun getCentroid(cluster:Any?) {
        var count = 0;
        var segs = track.analysis.segments;
        var vsum = zeroArray(segs[0][vname].length);

        for (i in 0 until segs.length) {
            if (segs[i][fname] === cluster) {
                count++;
                vsum = sumArray(vsum, segs[i][vname]);
            }
        }

        vsum = divArray(vsum, count);
        return vsum;
    }

    fun findNearestCluster(clusters:Array<Any?>, seg:Any?) {
        var shortestDistance = Number.MAX_VALUE;
        var bestCluster = -1;

        for (i in 0 until clusters.size) {
            var distance = euclidean_distance(clusters[i], seg[vname]);
            if (distance < shortestDistance) {
                shortestDistance = distance;
                bestCluster = i;
            }
        }
        return bestCluster;
    }

    // kmeans clusterer
    // use random initial assignments
    for (i in 0 until track.analysis.segments.length) {
        track.analysis.segments[i][fname] = Math.floor(Math.random() * numClusters);
    }

    reportClusteringStats();

    while (maxLoops-- > 0) {
        // calculate cluster centroids
        var centroids = mutableListOf<Any?>()
        for (i in 0 until numClusters) {
            centroids[i] = getCentroid(i);
        }
        // reassign segs to clusters
        var switches = 0;
        for (i in 0 until track.analysis.segments.length) {
            var seg = track.analysis.segments[i];
            var oldCluster = seg[fname];
            var newCluster = findNearestCluster(centroids, seg);
            if (oldCluster !== newCluster) {
                switches++;
                seg[fname] = newCluster;
            }
        }
        //console.log("loopleft", maxLoops, 'switches', switches);
        if (switches == 0) {
            break;
        }
    }
    reportClusteringStats();
}
