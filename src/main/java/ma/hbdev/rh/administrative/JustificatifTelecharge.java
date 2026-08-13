package ma.hbdev.rh.administrative;

import org.springframework.core.io.Resource;

record JustificatifTelecharge(Resource ressource, String nomOriginal, String typeMime) {}
