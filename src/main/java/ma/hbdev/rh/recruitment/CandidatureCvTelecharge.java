package ma.hbdev.rh.recruitment;

import org.springframework.core.io.Resource;

record CandidatureCvTelecharge(Resource ressource, String nomOriginal, String typeMime) {}
