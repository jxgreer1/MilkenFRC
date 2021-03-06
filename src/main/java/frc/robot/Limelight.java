package frc.robot;

import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.controller.ProfiledPIDController;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj.trajectory.TrapezoidProfile;
import frc.robot.Constants.VISION;
import frc.robot.lib.InterpolatingDouble;
import frc.robot.lib.MkUtil;
import frc.robot.lib.MkUtil.DriveSignal;

import static frc.robot.lib.MkUtil.limit;

public class Limelight {

  private final TrapezoidProfile.Constraints constraints = new TrapezoidProfile.Constraints(VISION.kMaxAimAngularVel, VISION.kMaxAimAngularAccel);
  private final ProfiledPIDController m_turn_controller = new ProfiledPIDController(VISION.kP_turn, VISION.kI_turn, VISION.kD_turn, constraints);
  private final NetworkTable table = NetworkTableInstance.getDefault().getTable("limelight");
  private final NetworkTableEntry tx = table.getEntry("tx");
  private final NetworkTableEntry ty = table.getEntry("ty");
  private final NetworkTableEntry led = table.getEntry("ledMode");
  private final NetworkTableEntry tv = table.getEntry("tv");
  private final NetworkTableEntry pipeline = table.getEntry("pipeline");
  private final Timer shootTimer = new Timer();
  private boolean hasTarget;
  private double distance, visionYaw, visionPitch;

  private Limelight() {
    table.getEntry("pipeline").setValue(VISION.kLimelightPipeline);
  }

  public static Limelight getInstance() {
    return InstanceHolder.mInstance;
  }

  public void updateSensors() {
    visionYaw = tx.getDouble(0.0);
    visionPitch = ty.getDouble(0.0);
    distance = getDistance(visionPitch);
    hasTarget = tv.getDouble(0.0) != 0.0f; //If tv returns 0, no valid target
  }

  public boolean inRange() {
    return hasTarget && Math.abs(visionYaw) < VISION.kShootAngleTol;
  }

  public void autoAimShoot(boolean ignoreAim) {
    updateAutoAimOutput();
    double curDist = getDistance();
    double RPM = VISION.kRPMMap.getInterpolated(new InterpolatingDouble(curDist)).value;
    double hoodDist = VISION.kHoodMap.getInterpolated(new InterpolatingDouble(curDist)).value;
    Shooter.getInstance().setShooterRPM(RPM);
    SmartDashboard.putNumber("Target RPM", RPM);
    Shooter.getInstance().setHoodPos(limit(hoodDist, -3.25, 0));
    double distance_mod = Constants.VISION.kElevatorDistanceConst * Limelight.getInstance().getDistance();
    double rpm_mod = VISION.kElevatorRpmConst * (RPM - Shooter.getInstance().getShooterRPM());
    Elevator.getInstance().setElevatorOutput(limit(0.40 - distance_mod, 0.2, 1.0)); //BEN-SLOWER IS FUCKING BETTER CAUSE IT WONT SLIP CAUSE RUBIN CANT USE CHAIN    60  this ---- what the hell does this accomplish, imma ask swerd maybe, also maybe gonna remove it and set it to 1 instead of limit math thing
    if ((ignoreAim || inRange()) && Math.abs(Shooter.getInstance().getShooterRPM() - RPM) < 30) { // 30 base
      ElevatorStopper.getInstance().setStopper(ElevatorStopper.StopperState.GO); //stops instead go since reversed
      Shooter.getInstance().setShootingMode(ignoreAim ? Shooter.ShootingMode.AUTO_SHOOTING_IGNORING_AIM : Shooter.ShootingMode.AUTO_SHOOTING_AIMED);
    } else {
      Shooter.getInstance().setShootingMode(Shooter.ShootingMode.AUTO_AIMIMG);
      ElevatorStopper.getInstance().setStopper(ElevatorStopper.StopperState.STOP); //gos instead stop since reversed //not supposed to be here
    }

    Intake.getInstance().setIntakeRoller(0.0);
    Intake.getInstance().setIntakeState(Intake.IntakeState.STOW);
  }

  public void updateAutoAimOutput() {
    double horizontal_angle = tx.getDouble(0.0);
    double turn_output = 0;
    if (Math.abs(horizontal_angle) > VISION.kAimAngleDeadband) {
      //how the hell do you know trapezoid profile and where did you even learn it 
      TrapezoidProfile trap = new TrapezoidProfile(constraints, new TrapezoidProfile.State(0, 0)); //https://docs.wpilib.org/en/stable/docs/software/advanced-controls/controllers/profiled-pidcontroller.html
      double turn_controllout_out = m_turn_controller.calculate(-horizontal_angle, 0);
      double feedforward = ((1.0) / (VISION.kMaxAimAngularVel)) * trap.calculate(Constants.kDt).velocity;
      SmartDashboard.putNumber("feedforward", feedforward); //TODO multiply by 100 or 1000 to see if value ever changes (is currently only 0, should be bigger value, idk)
      turn_output = MkUtil.limitAbsolute(turn_controllout_out + feedforward, Constants.VISION.kMaxAutoAimOutput);
    }
    Drive.getInstance().setOutput(new DriveSignal(turn_output, -turn_output));
  }

  public double getDistance() {
    return distance;
  }

  public void toggleLED() {
    if (led.getDouble(0.0) == 1) {
      table.getEntry("ledMode").setValue(3);
    } else {
      table.getEntry("ledMode").setValue(1);
    }
  }

  public double getDistance(double ty) {
    double height_camera_to_target = (89.75 - 33); //inches
    double lightlight_pitch = 17.7; //Degrees from horizontal
    return (height_camera_to_target / Math.tan(Math.toRadians(lightlight_pitch + ty)));
  }

  public void updateDashboard() {
    SmartDashboard.putNumber("Horizontal Angle", visionYaw);
    SmartDashboard.putNumber("Vertical Angle", visionPitch);
    SmartDashboard.putNumber("Target Distance", distance);
    SmartDashboard.putBoolean("In Range", inRange());
  }

  private static class InstanceHolder {

    private static final Limelight mInstance = new Limelight();
  }
}
