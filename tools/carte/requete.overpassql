[out:json][timeout:90];
area["ref:INSEE"="35051"]["boundary"="administrative"]->.c;
rel["ref:INSEE"="35051"]["boundary"="administrative"];out geom;
(way["waterway"="river"](area.c);rel["waterway"="river"]["name"="La Vilaine"];)->.r;.r out geom;
(way["natural"="water"](area.c);rel["natural"="water"](area.c);)->.w;.w out geom;
(way["landuse"~"^(forest|meadow|grass|recreation_ground|village_green)$"](area.c);way["leisure"~"^(park|golf_course|nature_reserve)$"](area.c);way["natural"~"^(wood|scrub|wetland)$"](area.c);rel["landuse"="forest"](area.c);rel["natural"="wood"](area.c);rel["leisure"="park"](area.c);)->.g;.g out geom;
(way["landuse"~"^(residential|commercial|industrial|retail)$"](area.c);rel["landuse"~"^(residential|commercial|industrial|retail)$"](area.c);)->.u;.u out geom;
nwr["amenity"="townhall"](area.c);out center tags;
