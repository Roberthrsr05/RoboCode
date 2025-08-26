package roberth;

import robocode.*;
import java.awt.Color;
import java.util.Random;

/**
 * Ddtank - robô apelão by Roberth Rafael
 */
public class Ddtank extends Robot {

    Random rand = new Random();

    public void run() {
        // Definindo cores
        setBodyColor(Color.lightGray);
        setGunColor(Color.red);
        setRadarColor(Color.white);
        setBulletColor(Color.green);
        setScanColor(Color.black);

        while (true) {
            // Movimento aleatório
            ahead(100 + rand.nextInt(100));
            turnRight(rand.nextInt(90) - 45);
            turnRadarRight(360); // radar girando sempre
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        double distancia = e.getDistance();

        // Mira no inimigo
        double absoluteBearing = getHeading() + e.getBearing();
        turnGunRight(normalizeBearing(absoluteBearing - getGunHeading()));

        // Atira com força proporcional à distância
        if (distancia < 135) {
            fire(3);
        } else {
            fire(1);
        }

        // Movimento estratégico
        if (distancia > 150) {
            ahead(distancia / 2);
        } else {
            back(50);
        }
    }

    public void onHitWall(HitWallEvent e) {
        back(50);
        turnRight(90);
    }

    public void onHitByBullet(HitByBulletEvent e) {
        turnRight(90 - e.getBearing());
        ahead(100);
    }

    public void onHitRobot(HitRobotEvent e) {
        if (e.isMyFault()) {
            back(50);
        }
        fire(3);
    }

    // Método auxiliar para normalizar ângulos entre -180 e 180
    private double normalizeBearing(double angle) {
        while (angle > 180) angle -= 360;
        while (angle < -180) angle += 360;
        return angle;
    }
}
