package it.unige.dibris.batchrmperm.controller;


import it.unige.dibris.batchrmperm.engine.ExecuteCmd;
import it.unige.dibris.batchrmperm.service.BatchWork;
import it.unige.dibris.batchrmperm.service.MalwarePermission;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@EnableAsync
@RestController
public class StartpointController {

    @Autowired
    BatchWork batchWork;

    @Autowired
    MalwarePermission malwarePermission;

    @RequestMapping(value = "start/{device}", method = RequestMethod.GET)
    public ResponseEntity<?> startTheWork(@PathVariable String device) {
        try {
            for (String dev : ExecuteCmd.devicesAttached()) {
                if (dev.equals(device)) {
                    batchWork.doTheWork(device);
                    return new ResponseEntity<>(HttpStatus.OK);
                }
            }
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @RequestMapping(value = "malw", method = RequestMethod.GET)
    public ResponseEntity<?> malwarePerm() {
        malwarePermission.doTheWork();
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
