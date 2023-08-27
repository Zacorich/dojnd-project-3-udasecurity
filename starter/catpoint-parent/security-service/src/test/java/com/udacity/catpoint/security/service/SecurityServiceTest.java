package com.udacity.catpoint.security.service;

import com.udacity.catpoint.image.service.ImageService;
import com.udacity.catpoint.security.data.*;
import com.udacity.catpoint.security.service.SecurityService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SecurityServiceTest {
    @Mock
    private SecurityRepository securityRepository;

    @Mock
    private ImageService imageService;

    private SecurityService securityService;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        securityService = new SecurityService(securityRepository, imageService);
        securityService.setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    /**
     * 1. If alarm is armed and a sensor becomes activated, put the system into pending alarm status.
     */
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    void testPendingAlarmWhenSensorActivatedWhileArmed(ArmingStatus armingStatus) {
        //create sensor
        Sensor sensor = new Sensor();
        sensor.setActive(false);

        //make sure repository returns some data for this test case
        //and set initial state of the securityService too
        when(securityRepository.getArmingStatus()).thenReturn(armingStatus);
        securityService.setArmingStatus(armingStatus);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        securityService.setAlarmStatus(AlarmStatus.NO_ALARM);

        //activate sensor
        securityService.changeSensorActivationStatus(sensor, true);

        //if mocked securityRepository is used directly from securityService without
        //in-memory storage it is impossible to test business logic as securityService's state
        //is represented by securityRepository
        //when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);

        //make sure system is in a pending alarm status
        assertEquals(AlarmStatus.PENDING_ALARM, securityService.getAlarmStatus());
    }

    /**
     * 2. If alarm is armed and a sensor becomes activated and the system is already pending
     * alarm, set the alarm status to alarm.
     */
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    public void testAlarmStatusBecomesAlarmWhenSensorActivatedWhileAlreadyPending(ArmingStatus armingStatus) {
        when(securityRepository.getArmingStatus()).thenReturn(armingStatus);
        securityService.setArmingStatus(armingStatus);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.setAlarmStatus(AlarmStatus.PENDING_ALARM);

        Sensor activeSensor = new Sensor();
        //initial sensor state is inactive
        activeSensor.setActive(false);

        // activate sensor
        securityService.changeSensorActivationStatus(activeSensor, true);

        // check the required alarm status
        assertEquals(AlarmStatus.ALARM, securityService.getAlarmStatus());
    }

    /**
     * 3. If pending alarm and all sensors are inactive, return to no alarm state.
     */
    @Test
    public void ifPendingAlarmAndAllSensorsInactive_returnToNoAlarmState() {
        Sensor sensor1 = new Sensor("Sensor A", SensorType.DOOR);
        sensor1.setActive(true);

        Sensor sensor2 = new Sensor("Sensor B", SensorType.MOTION);
        sensor2.setActive(true);

        Set<Sensor> sensors = Set.of(sensor1, sensor2);

        // setup mock repository with some values
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        lenient().when(securityRepository.getSensors()).thenReturn(sensors);

        // initial state of securityService
        securityService.setAlarmStatus(AlarmStatus.PENDING_ALARM);

        // change sensors states to trigger handleSensorDeactivated methods
        securityService.changeSensorActivationStatus(sensor1, false);
        securityService.changeSensorActivationStatus(sensor2, false);

        // Assert the Alarm status changed to AlarmStatus.NO_ALARM
        assertEquals(AlarmStatus.NO_ALARM, securityService.getAlarmStatus());
    }

    /**
     * 4. If alarm is active, change in sensor state should not affect the alarm state.
     */
    @Test
    void ifAlarmIsActive_changeInSensorStateShouldNotAffectAlarmState() {
        // Create motion sensor that is active
        Sensor sensor = new Sensor("Living Room", SensorType.MOTION);
        sensor.setActive(true);
        // Assuming the system is armed at home
        securityService.setAlarmStatus(AlarmStatus.ALARM);
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        // Deactivate sensor
        securityService.changeSensorActivationStatus(sensor, false);

        // Assert that the alarm status hasn't changed
        assertEquals(AlarmStatus.ALARM, securityService.getAlarmStatus());
    }

    /**
     * 5. If a sensor is activated while already active and the system is
     * in pending state, change it to alarm state.
     */
    @Test
    void ifSensorActivatedWhileAlreadyActiveAndPending_changeToAlarmState() {
        Sensor sensor = new Sensor("Living Room", SensorType.MOTION);
        sensor.setActive(true);

        // Set the initial states
        securityService.setAlarmStatus(AlarmStatus.PENDING_ALARM);
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        // Setup mock repository responses
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);

        // Activate the sensor while it's already active
        securityService.changeSensorActivationStatus(sensor, true);

        // Assert the Alarm status changed to AlarmStatus.ALARM
        assertEquals(AlarmStatus.ALARM, securityService.getAlarmStatus());
    }

}