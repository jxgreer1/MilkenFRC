package frc.robot.commands;

import edu.wpi.first.wpilibj2.command.CommandBase;
import frc.robot.Shooter;

public class SpinUp extends CommandBase {

  private double rpm;

  public SpinUp(double rpm) {
    this.rpm = rpm;
  }

  // Called just before this Command runs the first time
  @Override
  public void initialize() {

  }

  @Override
  public void execute() {
    Shooter.getInstance().setShooterRPM(rpm);
  }

  // Make this return true when this Command no longer needs to run execute()
  @Override
  public boolean isFinished() {
    return false;
  }

  // Called once after isFinished returns true
  @Override
  public void end(boolean interrupted) {

  }
}
