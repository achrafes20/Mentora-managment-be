package ma.hbdev.rh.config;

import org.springframework.core.io.Resource;

record SignatureEntrepriseTelecharge(Resource ressource, String typeMime) {}
