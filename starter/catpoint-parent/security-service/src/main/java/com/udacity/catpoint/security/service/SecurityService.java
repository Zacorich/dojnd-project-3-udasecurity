package com.udacity.catpoint.security.service;

import com.udacity.catpoint.image.service.ImageService;
import com.udacity.catpoint.security.application.StatusListener;
import com.udacity.catpoint.security.data.AlarmStatus;
import com.udacity.catpoint.security.data.ArmingStatus;
import com.udacity.catpoint.security.data.SecurityRepository;
import com.udacity.catpoint.security.data.Sensor;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

/**
 * Service that receives information about changes to the security system. Responsible for
 * forwarding updates to the repository and making any decisions about changing the system state.
 * <p>
 * This is the class that should contain most of the business logic for our system, and it is the
 * class you will be writing unit tests for.
 */
public class SecurityService {

    private ImageService imageService;
    private SecurityRepository securityRepository;
    private Set<StatusListener> statusListeners = new HashSet<>();

    private AlarmStatus alarmStatus = AlarmStatus.NO_ALARM;
    private ArmingStatus armingStatus = ArmingStatus.DISARMED;

    public SecurityService(SecurityRepository securityRepository, ImageService imageService) {
        this.securityRepository = securityRepository;
        this.imageService = imageService;
    }

    /**
     * Sets the current arming status for the system. Changing the arming status
     * may update both the alarm status.
     *
     * @param armingStatus
     */
    public void setArmingStatus(ArmingStatus armingStatus) {
        if (armingStatus == ArmingStatus.DISARMED) {
            setAlarmStatus(AlarmStatus.NO_ALARM);
        }
        this.armingStatus = armingStatus;
        securityRepository.setArmingStatus(armingStatus);
    }

    /**
     * Internal method that handles alarm status changes based on whether
     * the camera currently shows a cat.
     *
     * @param cat True if a cat is detected, otherwise false.
     */
    private void catDetected(Boolean cat) {
        if (cat && getArmingStatus() == ArmingStatus.ARMED_HOME) {
            setAlarmStatus(AlarmStatus.ALARM);
        } else {
            setAlarmStatus(AlarmStatus.NO_ALARM);
        }

        statusListeners.forEach(sl -> sl.catDetected(cat));
    }

    /**
     * Register the StatusListener for alarm system updates from within the SecurityService.
     *
     * @param statusListener
     */
    public void addStatusListener(StatusListener statusListener) {
        statusListeners.add(statusListener);
    }

    public void removeStatusListener(StatusListener statusListener) {
        statusListeners.remove(statusListener);
    }

    /**
     * Change the alarm status of the system and notify all listeners.
     *
     * @param status
     */
    public void setAlarmStatus(AlarmStatus status) {
        this.alarmStatus = status;
        securityRepository.setAlarmStatus(status);
        statusListeners.forEach(sl -> sl.notify(status));
    }

    /**
     * Internal method for updating the alarm status when a sensor has been activated.
     */
    private void handleSensorActivated() {
        //sensor change doesn't effect armed system
        if (getAlarmStatus() == AlarmStatus.ALARM) {
            return;
        }

        if (getArmingStatus() == ArmingStatus.DISARMED) {
            return; //no problem if the system is disarmed
        }
        switch (getAlarmStatus()) {
            case NO_ALARM -> setAlarmStatus(AlarmStatus.PENDING_ALARM);
            case PENDING_ALARM -> setAlarmStatus(AlarmStatus.ALARM);
        }

    }

    /**
     * Internal method for updating the alarm status when a sensor has been deactivated
     */
    private void handleSensorDeactivated(Sensor sensorBeingModified) {
        //sensor change doesn't effect armed system
        if (getAlarmStatus() == AlarmStatus.ALARM) {
            return;
        }

        // If any other sensor (excluding the one being modified) is active, we don't change the status
        boolean anyOtherSensorActive = securityRepository.getSensors().stream()
                .filter(s -> !s.equals(sensorBeingModified))
                .anyMatch(Sensor::getActive);

        if (anyOtherSensorActive || sensorBeingModified.getActive()) {
            return;
        }


        switch (getAlarmStatus()) {
            case PENDING_ALARM -> setAlarmStatus(AlarmStatus.NO_ALARM);
            case ALARM -> setAlarmStatus(AlarmStatus.PENDING_ALARM);
        }
    }

    /**
     * Change the activation status for the specified sensor and update alarm status if necessary.
     *
     * @param sensor
     * @param active
     */
    public void changeSensorActivationStatus(Sensor sensor, Boolean active) {
        // If a sensor is activated while already active and the system is in pending state,
        // change it to alarm state.
        if(active && sensor.getActive() && getAlarmStatus() == AlarmStatus.PENDING_ALARM) {
            setAlarmStatus(AlarmStatus.ALARM);
            return;
        }

        if (!sensor.getActive() && active) {
            handleSensorActivated();
        } else if (sensor.getActive() && !active) {
            sensor.setActive(active);
            handleSensorDeactivated(sensor);
        }
        sensor.setActive(active);
        securityRepository.updateSensor(sensor);
    }

    /**
     * Send an image to the SecurityService for processing. The securityService will use its provided
     * ImageService to analyze the image for cats and update the alarm status accordingly.
     *
     * @param currentCameraImage
     */
    public void processImage(BufferedImage currentCameraImage) {
        catDetected(imageService.imageContainsCat(currentCameraImage, 50.0f));
    }

    public AlarmStatus getAlarmStatus() {
        //priority in-memory value
        if(securityRepository.getAlarmStatus() == null ||
                securityRepository.getAlarmStatus() != alarmStatus){
            return alarmStatus;
        }
        return securityRepository.getAlarmStatus();
    }

    public Set<Sensor> getSensors() {
        return securityRepository.getSensors();
    }

    public void addSensor(Sensor sensor) {
        securityRepository.addSensor(sensor);
    }

    public void removeSensor(Sensor sensor) {
        securityRepository.removeSensor(sensor);
    }

    public ArmingStatus getArmingStatus() {
        //priority in-memory value
        if(securityRepository.getArmingStatus() == null ||
                securityRepository.getArmingStatus() != armingStatus){
            return armingStatus;
        }
        return securityRepository.getArmingStatus();
    }
}
