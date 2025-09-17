import robocode.*;
import robocode.util.Utils;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class MEU_ROBO_1_0 extends AdvancedRobot {
    private static class Enemy {
        double distance;
        double bearing;
        double heading;
        double velocity;
        double energy;
        double x, y;
        long lastSeenTime;
    }

    private final Map<String, Enemy> enemies = new HashMap<>();
    private String currentTarget = null;
    private int movementDirection = 1;
    private long lastReverseTime = 0;

    private static final double WALL_STICK = 120;
    private static final long MIN_REVERSE_INTERVAL = 20;
    private static final double SAFE_WALL_DIST = 80;

    public void run() {
        setColors(Color.PINK, Color.PINK, Color.PINK, Color.PINK, Color.PINK);
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
        while (true) {
            if (currentTarget != null && enemies.containsKey(currentTarget)) {
                Enemy e = enemies.get(currentTarget);
                double angleToEnemy = Math.atan2(e.x - getX(), e.y - getY());
                double radarTurn = Utils.normalRelativeAngle(angleToEnemy - Math.toRadians(getRadarHeading()));
                setTurnRadarRightRadians(radarTurn * 2);
            } else {
                setTurnRadarRight(45);
            }
            execute();
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        Enemy enemy = enemies.getOrDefault(e.getName(), new Enemy());
        enemy.distance = e.getDistance();
        enemy.bearing = e.getBearingRadians();
        enemy.heading = e.getHeadingRadians();
        enemy.velocity = e.getVelocity();
        enemy.energy = e.getEnergy();
        enemy.lastSeenTime = getTime();
        double absoluteBearing = Math.toRadians(getHeading()) + e.getBearingRadians();
        enemy.x = getX() + Math.sin(absoluteBearing) * e.getDistance();
        enemy.y = getY() + Math.cos(absoluteBearing) * e.getDistance();
        enemies.put(e.getName(), enemy);
        chooseTarget();
        double radarTurn = Utils.normalRelativeAngle(absoluteBearing - Math.toRadians(getRadarHeading()));
        setTurnRadarRightRadians(radarTurn * 2);
        doMovement(enemy);
        doGun(enemy);
        execute();
    }

    private void chooseTarget() {
        String best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        long now = getTime();
        for (Map.Entry<String, Enemy> entry : enemies.entrySet()) {
            Enemy en = entry.getValue();
            if (now - en.lastSeenTime > 50) continue;
            if (en.distance < bestDist) {
                bestDist = en.distance;
                best = entry.getKey();
            }
        }
        currentTarget = best;
    }

    private void doMovement(Enemy enemy) {
        if (enemy == null) {
            setAhead(100);
            return;
        }
        if (getTime() - lastReverseTime > MIN_REVERSE_INTERVAL) {
            if (Math.random() < 0.06) {
                movementDirection *= -1;
                lastReverseTime = getTime();
            }
        }
        double absoluteBearing = Math.atan2(enemy.x - getX(), enemy.y - getY());
        double moveAngle = Utils.normalRelativeAngle(absoluteBearing + Math.PI/2 * movementDirection);
        double smoothedAngle = wallSmoothing(getX(), getY(), moveAngle, movementDirection);
        double turnAngleDeg = Math.toDegrees(Utils.normalRelativeAngle(smoothedAngle - Math.toRadians(getHeading())));
        setTurnRight(normalizeDegrees(turnAngleDeg));
        setAhead(150 * movementDirection);
    }

    private double wallSmoothing(double x, double y, double angle, int dir) {
        double battlefieldWidth = getBattleFieldWidth();
        double battlefieldHeight = getBattleFieldHeight();
        if (x < SAFE_WALL_DIST || x > battlefieldWidth - SAFE_WALL_DIST ||
            y < SAFE_WALL_DIST || y > battlefieldHeight - SAFE_WALL_DIST) {
            double centerX = battlefieldWidth / 2;
            double centerY = battlefieldHeight / 2;
            angle = Math.atan2(centerX - x, centerY - y);
        }
        double testX = x;
        double testY = y;
        int checks = 0;
        double a = angle;
        while (checks++ < 16) {
            testX = x + Math.sin(a) * WALL_STICK;
            testY = y + Math.cos(a) * WALL_STICK;
            if (testX > WALL_STICK && testX < battlefieldWidth - WALL_STICK &&
                testY > WALL_STICK && testY < battlefieldHeight - WALL_STICK) {
                return a;
            }
            a += dir * Math.toRadians(10);
        }
        return angle;
    }

    private void doGun(Enemy enemy) {
        if (enemy == null) return;
        double firePower = Math.min(3.0, Math.max(0.8, 400.0 / enemy.distance));
        if (getEnergy() < 15) firePower = Math.min(firePower, 1.0);
        double bulletSpeed = 20 - 3 * firePower;
        double timeToHit = enemy.distance / bulletSpeed;
        double predictedX = enemy.x + Math.sin(enemy.heading) * enemy.velocity * timeToHit;
        double predictedY = enemy.y + Math.cos(enemy.heading) * enemy.velocity * timeToHit;
        predictedX = Math.max(18, Math.min(getBattleFieldWidth() - 18, predictedX));
        predictedY = Math.max(18, Math.min(getBattleFieldHeight() - 18, predictedY));
        double dx = predictedX - getX();
        double dy = predictedY - getY();
        double aimAngle = Math.atan2(dx, dy);
        double gunTurn = Utils.normalRelativeAngle(aimAngle - Math.toRadians(getGunHeading()));
        setTurnGunRightRadians(gunTurn);
        if (Math.abs(Math.toDegrees(gunTurn)) < Math.max(2.0, 8.0 / Math.max(0.5, firePower))) {
            if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 10) {
                setFire(clamp(firePower, 0.1, 3.0));
            }
        }
    }

    public void onHitByBullet(HitByBulletEvent e) {
        movementDirection *= -1;
        lastReverseTime = getTime();
    }

    public void onHitWall(HitWallEvent e) {
        movementDirection *= -1;
        setBack(80);
    }

    public void onHitRobot(HitRobotEvent e) {
        if (e.isMyFault()) {
            setBack(60);
        } else {
            double firePower = 3;
            if (getGunHeat() == 0) {
                setFire(firePower);
            }
        }
    }

    public void onRobotDeath(RobotDeathEvent e) {
        enemies.remove(e.getName());
        if (e.getName().equals(currentTarget)) currentTarget = null;
    }

    public void onBulletHit(BulletHitEvent e) {}

    private double normalizeDegrees(double ang) {
        while (ang > 180) ang -= 360;
        while (ang < -180) ang += 360;
        return ang;
    }

    private double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}