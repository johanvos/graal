/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

var flamegraph, flamegraph_details, search_matches, fg_xmin, fg_xmax, fg_zoomed_sample, fg_max_depth, fg_svg;
function fg_init(evt) {
    flamegraph_details = document.getElementById("details").firstChild;
    searchbtn = document.getElementById("search");
    matchedtxt = document.getElementById("matched");
    flamegraph = document.getElementById("flamegraph");
    search_matches = [];
    fg_xmin = 0;
    fg_xmax = profileData.h;
    fg_zoomed_sample = profileData;
    var el = flamegraph.getElementsByTagName("g");
    for(let i=0;i<el.length;i++)
        update_text(el[i]);
}

function fg_element_for_sample(sample) {
    if (sample.hasOwnProperty("fg_element")) {
        return sample.fg_element;
    } else {
        let e = document.getElementById("f_" + sample.id);
        sample.fg_element = e;
        return e;
    }
}

function fg_create_element_for_sample(sample, width, x) {
    let y = (depth_for_sample(sample) + 1) * -fg_frameheight - fg_bottom_padding;
    let e = document.createElementNS("http://www.w3.org/2000/svg", "g");
    e.className.baseVal = "func_g";
    e.onmouseover = function(e) {s(this)};
    e.onmouseout = function(e) {c(this)};
    e.onclick = function(e) {zoom(this)};
    e.id =  "f_" + sample.id;

    let title = document.createElementNS("http://www.w3.org/2000/svg", "title");
    title.textContent = "Blah";

    let r = document.createElementNS("http://www.w3.org/2000/svg", "rect");
    r.x.baseVal.value = x;
    r.y.baseVal.value = y;
    r.width.baseVal.value = width;
    r.height.baseVal.value = fg_frameheight
    if (sample.searchMatch) {
        r.style.fill =  searchColor;
    } else {
        r.style.fill = fg_color_for_sample(color_type, sample);
    }
    r.rx.baseVal.value = 2;
    r.ry.baseVal.vlaue = 2;

    let t = document.createElementNS("http://www.w3.org/2000/svg", "text");
    t.style.textAnchor = "left";
    t.setAttribute("x", x + 3);
    t.setAttribute("y", y - 5 + fg_frameheight);
    t.style.fontSize = fontSize;
    t.style.fontFamily = "Verdana";
    t.style.fill = "rgb(0, 0, 0)";

    e.appendChild(title);
    e.appendChild(r);
    e.appendChild(t);
    flamegraph.appendChild(e);
    sample.fg_element = e;
    return e;
}

// zoom
function zoom_child(sample) {
    let width =  sample.h / (fg_xmax - fg_xmin) * (fg_width - 2 * xpad);
    let x = (sample.x - fg_xmin) / (fg_xmax - fg_xmin) * (fg_width - 2 * xpad) + xpad;
    let e = fg_element_for_sample(sample);

    if (width < fg_min_width) {
        if (e != null) {
            e.style["display"] = "none";
        }
        return;
    } else {
        let depth = depth_for_sample(sample);
        if (depth > fg_max_depth) {
            fg_max_depth = depth;
        }
    }
    if (e == null) {
        e = fg_create_element_for_sample(sample, width, x);
    } else {
        e.style["display"] = "block";
        e.style["opacity"] = "1";
    }

    let title = e.firstElementChild;
    let r = e.children[1];
    let t = e.lastElementChild;
    let name = name_for_sample(sample);
    let source = source_for_sample(sample);

    title.textContent = name + " (" + languageNames[sample.l] + ")\n" +
        "Self samples: " + (sample.i + sample.c) + " (" + (100 * (sample.c + sample.i) / (fg_xmax - fg_xmin)).toFixed(2) + "%)\n" +
        "Total samples: " + (sample.h) + " (" + (100 * (sample.h + sample.i) / (fg_xmax - fg_xmin)).toFixed(2) + "%)\n" +
        "Source location: " + source + ":" + sample.fl + "\n";

    r.x.baseVal.value = x;
    t.x.baseVal[0].value = x + 3;

    r.width.baseVal.value = width;
    update_text_parts(e, r, t, width - 3, name);
}

function zoom_parent(sample) {
    let width =  fg_width - 2 * xpad;
    let x = xpad;
    let e = fg_element_for_sample(sample);
    if (e != null) {
        e.style["display"] = "block";
        e.style["opacity"] = "0.5";

        let title = e.firstElementChild;
        let r = e.children[1];
        let t = e.lastElementChild;

        title.textContent = "Function: " + name + "\n" +
            sample.h + " samples (" + sample.i + " interpreted, " + sample.c + " compiled).\n" +
            " Parent of displayed sample range.\n";

        r.x.baseVal.value = x;
        t.x.baseVal[0].value = x + 3;

        r.width.baseVal.value = width;

        update_text_parts(e, r, t, width - 3, name_for_sample(sample).replace(/\\([^(]*\\)$/,""));
    }
}

function zoom(node) {
    let data = sample_parents_and_unrelated_for_id(node.getAttribute("id").substring(2));
    let sample = data[0];
    let parents = data[1];
    let unrelated = data[2];
    fg_zoomed_sample = sample;
    fg_xmin = sample.x;
    fg_xmax = sample.x + sample.h;
    fg_max_depth = 0;

    var unzoombtn = document.getElementById("unzoom");
    unzoombtn.style["opacity"] = "1.0";

    zoom_internal(sample, parents, unrelated);
}

function fg_canvas_resize() {
    let height = fg_frameheight * (fg_max_depth + 1) + fg_top_padding + fg_bottom_padding;
    let svg = document.firstElementChild;
    let fg_svg = flamegraph.parentElement;
    let old_height = fg_svg.height.baseVal.value;
    fg_svg.height.baseVal.value = height;
    let viewBox = fg_svg.viewBox.baseVal;
    viewBox.y = -height;
    viewBox.height = height;
    reposition_histogram(height);
    let fg_canvas = document.getElementById("fg_canvas");
    fg_canvas.height.baseVal.value = height;
    fg_canvas.y.baseVal.value = -height;
    svg.height.baseVal.value = svg.height.baseVal.value - old_height + height;
    svg.viewBox.baseVal.height = svg.height.baseVal.value;
}

function zoom_internal(sample, parents, unrelated) {
    for (const u of unrelated) {
        let iter = sample_and_children_depth_first(u);
        let c = iter.next();
        while (!c.done) {
            let e = fg_element_for_sample(c.value);
            if (e != null) {
                e.style["display"] = "none";
            }
            c = iter.next();
        }
    }
    for (const p of parents) {
        zoom_parent(p);
    }
    let iter = sample_and_children_depth_first(sample);
    let c = iter.next();
    while (!c.done) {
        zoom_child(c.value);
        c = iter.next();
    }
    fg_search_update();
    fg_canvas_resize();
    rebuild_histogram(sample);
}

function unzoom() {
    var unzoombtn = document.getElementById("unzoom");
    unzoombtn.style["opacity"] = "0.1";

    fg_xmin = 0;
    fg_xmax = profileData.h
    fg_max_depth = 0;
    fg_zoomed_sample = profileData;

    zoom_internal(profileData, [], []);
}

// search
function fg_search(term) {
    var re = new RegExp(term);

    search_matches = []

    let iter = sample_and_children_depth_first(profileData);
    let c = iter.next();
    while (!c.done) {
        let sample = c.value;
        if (name_for_sample(sample).match(re)) {
            sample.searchMatch = true;
            search_matches.push(sample);
            let e = fg_element_for_sample(sample);
            if (e != null) {
                let r = e.children[1];
                r.style.fill = searchColor;
            }
        }
        c = iter.next();
    }

    if (search_matches.length == 0)
        return;

    searchbtn.style["opacity"] = "0.8";
    searchbtn.firstChild.nodeValue = "Reset Search"

    fg_search_update();
}

function fg_search_update() {
    let samples = search_matches.slice();

    if (samples.length == 0) {
        matchedtxt.style["opacity"] = "0.0";
        matchedtxt.firstChild.nodeValue = ""
        return;
    }

    samples.sort(function(a, b){
        if ((a.x - b.x) == 0) {
            if ((b.h - a.h) == 0) {
                return a.id - b.id;
            } else {
                return b.h - a.h;
            }
        } else {
            return a.x - b.x;
        }
    });

    let count = 0;
    let lastx = 0;
    let nextx = 0;

    for (const sample of samples) {
        // Skip over anything outside our zoom or up stack from the zoomed element.
        if (sample.x < fg_xmin || sample.x + sample.h > fg_xmax || sample.id < fg_zoomed_sample.id) {
            continue;
        }
        if (sample.x >= nextx) {
            lastx = sample.x;
            nextx = lastx + sample.h;
            count += nextx - lastx;
        }
    }


    // display matched percent
    matchedtxt.style["opacity"] = "1.0";
    let pct = 100 * count / (fg_xmax - fg_xmin);
    if (pct == 100) {
        pct = "100"
    } else {
        pct = pct.toFixed(1)
    }
    matchedtxt.firstChild.nodeValue = "Matched: " + pct + "%";
}

function fg_searchover(e) {
    searchbtn.style["opacity"] = "1.0";
}

function fg_searchout(e) {
    searchbtn.style["opacity"] = "0.8";
}

function fg_reset_search() {
    for (const match of search_matches) {
        match.searchMatch = false;
        let e = fg_element_for_sample(match);
        if (e != null) {
            let r = e.children[1];
            let color = match.currentColor;
            if (color == undefined) {
                color = fg_color_for_sample("fg", match);
            }
            r.style.fill = color;
        }
    }
    search_matches = [];
}

function fg_update_color(color_type) {
    let iter = sample_and_children_depth_first(profileData);
    let c = iter.next();
    while (!c.done) {
        let sample = c.value;
        let color = fg_color_for_sample(color_type, sample);
        sample.currentColor = color;
        if (sample.searchMatch != true) {
            let e = fg_element_for_sample(sample);
            if (e != null) {
                let r = e.children[1];
                r.style.fill = color;
            }
        }
        c = iter.next();
    }
}

function fg_color_for_sample(color_type, sample) {
    if (color_type == "fg") {
        return color_for_name(0, name_for_sample(sample));
    } else if (color_type == "bl") {
        return color_for_name(sample.l, name_for_sample(sample));
    } else if (color_type = "bc") {
        return color_for_compilation(sample.i, sample.c);
    }
}

function fg_resize(new_width) {
    fg_width = new_width
    let fg_svg = flamegraph.parentElement;
    fg_svg.width.baseVal.value = new_width;
    let viewbox = fg_svg.viewBox.baseVal;
    viewbox.width = new_width
    let fg_canvas = document.getElementById("fg_canvas");
    fg_canvas.width.baseVal.value = new_width;
    if (fg_zoomed_sample == profileData) {
        unzoom();
    } else {
        zoom(fg_element_for_sample(fg_zoomed_sample));
    }
    document.getElementById("fg_title").setAttribute("x", new_width / 2);
    document.getElementById("fg_help").setAttribute("x", new_width / 2);
    document.getElementById("search").setAttribute("x", new_width - xpad);

}
