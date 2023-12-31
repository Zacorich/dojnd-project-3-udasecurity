package com.udacity.catpoint.security.service;

import com.udacity.catpoint.image.service.ImageService;
import com.udacity.catpoint.security.data.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
        // create sensor
        Sensor sensor = new Sensor();
        sensor.setActive(false);

        // make sure repository returns some data for this test case
        // and set initial state of the securityService too
        when(securityRepository.getArmingStatus()).thenReturn(armingStatus);
        securityService.setArmingStatus(armingStatus);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        securityService.setAlarmStatus(AlarmStatus.NO_ALARM);

        // activate sensor
        securityService.changeSensorActivationStatus(sensor, true);

        // if mocked securityRepository is used directly from securityService without
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
        // initial sensor state is inactive
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

        // check the alarm status changed to AlarmStatus.NO_ALARM
        assertEquals(AlarmStatus.NO_ALARM, securityService.getAlarmStatus());
    }

    /**
     * 4. If alarm is active, change in sensor state should not affect the alarm state.
     */
    @Test
    void ifAlarmIsActive_changeInSensorStateShouldNotAffectAlarmState() {
        // create a motion sensor that is active
        Sensor sensor = new Sensor("Living Room", SensorType.MOTION);
        sensor.setActive(true);

        // init system alarm and arming to be ALARM and ARMED_HOME
        securityService.setAlarmStatus(AlarmStatus.ALARM);
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        // deactivate sensor
        securityService.changeSensorActivationStatus(sensor, false);

        // check that the alarm status didn't change
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

        // set the initial states
        securityService.setAlarmStatus(AlarmStatus.PENDING_ALARM);
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        // setup mock repository responses
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);

        // activate the sensor while it's already active
        securityService.changeSensorActivationStatus(sensor, true);

        // check the alarm status changed to AlarmStatus.ALARM
        assertEquals(AlarmStatus.ALARM, securityService.getAlarmStatus());
    }

    /**
     * 6. If a sensor is deactivated while already inactive, make no changes to the alarm state.
     */
    @Test
    void ifSensorDeactivatedWhileAlreadyInactive_noChangesToAlarmState() {
        // create sensor
        Sensor sensor = new Sensor("Front Door", SensorType.DOOR);
        sensor.setActive(false);

        // init system with some state
        AlarmStatus initialAlarmStatus = AlarmStatus.PENDING_ALARM;
        securityService.setAlarmStatus(initialAlarmStatus);

        // deactivate sensor
        securityService.changeSensorActivationStatus(sensor, false);

        // Assert that the alarm status hasn't changed
        assertEquals(initialAlarmStatus, securityService.getAlarmStatus());
    }

    /**
     * 7. If the image service identifies an image containing a cat while
     * the system is armed-home, put the system into alarm status.
     */
    @Test
    void ifImageServiceIdentifiesCat_whenSystemIsArmedHome_goToAlarmStatus() {
        // fake the imageService that a cat is found in the image
        BufferedImage fakeImage = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        when(imageService.imageContainsCat(fakeImage, 50.0f)).thenReturn(true);

        // set the system to ARMED_HOME
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        // process the image using the securityService
        securityService.processImage(fakeImage);

        // check that the system changed into alarm status
        assertEquals(AlarmStatus.ALARM, securityService.getAlarmStatus());
    }

    /**
     * 8. If the image service identifies an image that does not contain a cat, change the status
     * to no alarm as long as the sensors are not active.
     */
    @Test
    void ifImageServiceDoesNotIdentifyCat_andSensorsAreInactive_setNoAlarm() {
        // provide fake image to imageService and mock that no cat was found
        BufferedImage fakeImage = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB); // create a fake image
        when(imageService.imageContainsCat(fakeImage, 50.0f)).thenReturn(false);

        // create inactive sensors
        Sensor windowSensor = new Sensor("Window", SensorType.WINDOW);
        Sensor doorSensor = new Sensor("Door", SensorType.DOOR);
        windowSensor.setActive(false);
        doorSensor.setActive(false);
        Set<Sensor> inactiveSensors = new HashSet<>(Set.of(doorSensor, windowSensor));

        // mock the sensors call
        when(securityRepository.getSensors()).thenReturn(inactiveSensors);

        //trigger getSensors()
        securityRepository.getSensors();

        // process the image
        securityService.processImage(fakeImage);

        // check if system changed to no alarm status
        assertEquals(AlarmStatus.NO_ALARM, securityService.getAlarmStatus());
    }

    /**
     * 9. If the system is disarmed, set the status to no alarm.
     */
    @Test
    void ifSystemIsDisarmed_setStatusToNoAlarm() {
        // init system
        securityService.setAlarmStatus(AlarmStatus.ALARM);

        // disarm the system
        securityService.setArmingStatus(ArmingStatus.DISARMED);

        // check if status changed to NO_ALARM
        assertEquals(AlarmStatus.NO_ALARM, securityService.getAlarmStatus());
    }

    /**
     * 10. If the system is armed, reset all sensors to inactive.
     */
    @Test
    void ifSystemIsArmed_allSensorsShouldBeInactive() {
        Sensor activeSensor1 = new Sensor("Door", SensorType.DOOR);
        activeSensor1.setActive(true);

        Sensor activeSensor2 = new Sensor("Motion", SensorType.MOTION);
        activeSensor2.setActive(true);

        //mock repository to return sensors needed
        Set<Sensor> sensors = new HashSet<>(Set.of(activeSensor1, activeSensor2));
        when(securityRepository.getSensors()).thenReturn(sensors);

        // set arming status
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        // check all sensors to be inactive
        for (Sensor sensor : securityService.getSensors()) {
            assertFalse(sensor.getActive());
        }
    }

    /**
     * 11. If the system is armed-home while the camera shows a cat, set the alarm status to alarm.
     */
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    void ifSystemIsArmedHomeAndCameraShowsCat_setAlarmStatusToAlarm(ArmingStatus armingStatus) {
        // mock the image service that the image cointains the cat
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);

        // set system arming status as required and alarm satus to NO_ALARM
        securityService.setArmingStatus(armingStatus);
        securityService.setAlarmStatus(AlarmStatus.NO_ALARM);

        // trigger image processing and mockito stub
        BufferedImage catImage = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        securityService.processImage(catImage);

        // check the system status change to ALARM
        assertEquals(AlarmStatus.ALARM, securityService.getAlarmStatus());
    }


    // Further tests for Application Requirements Tip to pass

    /**
     * Tip 1: Arm the system and activate two sensors; the system should go to the Alarm state.
     * Then deactivate one sensor, and the system should not change the alarm state.
     */
    @Test
    void testAlarmStatusRemainsAfterDeactivatingSingleSensor() {
        Sensor sensor1 = new Sensor("Sensor A", SensorType.DOOR);
        Sensor sensor2 = new Sensor("Sensor B", SensorType.WINDOW);

        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        securityService.changeSensorActivationStatus(sensor1, true);
        securityService.changeSensorActivationStatus(sensor2, true);

        assertEquals(AlarmStatus.ALARM, securityService.getAlarmStatus());

        securityService.changeSensorActivationStatus(sensor1, false);

        assertEquals(AlarmStatus.ALARM, securityService.getAlarmStatus());
    }

    /**
     * Tip 2: Arm the system, scan a picture until it detects a cat, the system should go to ALARM state,
     * scan a picture again until there is no cat, the system should go to NO ALARM state.
     */
    @Test
    void testAlarmStatusTogglesOnCatPresence() {
        // true for first image and then false for second image
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true, false);

        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        // process image with a cat on it
        BufferedImage catImage = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        securityService.processImage(catImage);

        // check that the system changed to ALARM
        assertEquals(AlarmStatus.ALARM, securityService.getAlarmStatus());

        // process image without a cat on it
        BufferedImage noCatImage = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        securityService.processImage(noCatImage);

        // check that system change to NO_ALARM state
        assertEquals(AlarmStatus.NO_ALARM, securityService.getAlarmStatus());
    }

    /**
     * Tip 3: Even when a cat is detected in the image, the system should go to the NO ALARM state when deactivated.
      */
    @Test
    void testSystemDeactivationResetsAlarmEvenAfterCatDetection() {
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);

        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        BufferedImage catImage = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        securityService.processImage(catImage);

        assertEquals(AlarmStatus.ALARM, securityService.getAlarmStatus());

        securityService.setArmingStatus(ArmingStatus.DISARMED);

        assertEquals(AlarmStatus.NO_ALARM, securityService.getAlarmStatus());
    }

    /**
     * Tip 4: Arm the system, scan a picture until it detects a cat, activate a sensor, and scan a picture again until
     * there is no cat; the system should still be in the alarm state as there is a sensor active.
     */
    @Test
    void testSensorActivationKeepsAlarmOnWithoutCatDetection() {
        Sensor sensor1 = new Sensor("Living Room", SensorType.MOTION);
        sensor1.setActive(false);
        Sensor sensor2 = new Sensor("Living Room", SensorType.MOTION);
        sensor2.setActive(false);

        //mock repository to return sensors needed
        Set<Sensor> sensors = new HashSet<>(Set.of(sensor1, sensor2));
        when(securityRepository.getSensors()).thenReturn(sensors);

        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);

        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        BufferedImage catImage = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        securityService.processImage(catImage);

        assertEquals(AlarmStatus.ALARM, securityService.getAlarmStatus());

        securityService.changeSensorActivationStatus(sensor1, true);

        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(false);

        BufferedImage noCatImage = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        securityService.processImage(noCatImage);

        assertEquals(AlarmStatus.ALARM, securityService.getAlarmStatus());
    }

    /**
     * Tip 5: Sensors were not reset to inactive when the system was armed: put all sensors to the active state
     * when disarmed, then put the system in the armed state; sensors should be inactivated.
     */
    @Test
    void testSensorsResetToInactiveWhenSystemIsArmed() {

        Sensor sensor1 = new Sensor("Sensor A", SensorType.DOOR);
        sensor1.setActive(true);

        Sensor sensor2 = new Sensor("Sensor B", SensorType.WINDOW);
        sensor2.setActive(true);

        Set<Sensor> sensors = Set.of(sensor1, sensor2);

        when(securityRepository.getSensors()).thenReturn(sensors);
        securityService.setArmingStatus(ArmingStatus.DISARMED);

        assertTrue(securityService.getSensors().stream().allMatch(Sensor::getActive));

        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        assertTrue(securityService.getSensors().stream().noneMatch(Sensor::getActive));
    }

    /**
     * Tip 6: Put the system as disarmed, scan a picture until it detects a cat after that,
     * make it armed, it should make the system in the ALARM state.
     */
    @Test
    void whenSystemIsDisarmedAndCatDetected_thenArmed_shouldTriggerAlarm() {
        securityService.setArmingStatus(ArmingStatus.DISARMED);

        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        securityService.processImage(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));

        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        assertEquals(AlarmStatus.ALARM, securityService.getAlarmStatus());
    }

    @Test
    void testAlarmStatusWithCatImageAndSensorActivationChanges() {
        Sensor sensor1 = new Sensor("Living Room", SensorType.MOTION);
        sensor1.setActive(false);
        Sensor sensor2 = new Sensor("Living Room", SensorType.MOTION);
        sensor2.setActive(false);

        Set<Sensor> sensors = new HashSet<>(Set.of(sensor1, sensor2));
        when(securityRepository.getSensors()).thenReturn(sensors);

        securityService.setArmingStatus(ArmingStatus.DISARMED);

        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        BufferedImage catImage = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        securityService.processImage(catImage);

        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        securityService.processImage(catImage);

        assertEquals(AlarmStatus.ALARM, securityService.getAlarmStatus());

        securityService.changeSensorActivationStatus(sensor1, true);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(false);
        BufferedImage noCatImage = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        securityService.processImage(noCatImage);
        assertEquals(AlarmStatus.ALARM, securityService.getAlarmStatus());
    }


}