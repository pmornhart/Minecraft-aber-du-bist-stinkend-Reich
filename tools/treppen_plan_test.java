// Probiert den Treppen-Planer mit vielen Zielen durch (ohne Minecraft).
// Aufruf: java tools/treppen_plan_test.java  (kopiert TreppenPlan.java vorher nach build/treppenplan/)
// Einfacher: tools/treppen_plan_test.sh
import de.moinmornhart.stinkendreich.strasse.TreppenPlan;

import java.util.Random;

public class treppen_plan_test {
    public static void main(String[] a) {
        Random r = new Random(1);
        int fehler = 0, leer = 0, faelle = 0, laengste = 0;
        for (int i = 0; i < 20000; i++) {
            int zx = r.nextInt(81) - 40, zz = r.nextInt(81) - 40, zy = -(4 + r.nextInt(130));
            int erste = r.nextInt(5) - 1;
            var plan = TreppenPlan.planen(0, 0, 0, zx, zy, zz, erste);
            faelle++;
            if (plan.isEmpty()) {
                leer++;
                continue;
            }
            var l = plan.get(plan.size() - 1);
            if (l.x() != zx || l.y() != zy || l.z() != zz) {
                fehler++;
                System.out.println("Ziel verfehlt " + zx + " " + zy + " " + zz);
            }
            int k = TreppenPlan.konflikte(plan);
            if (k > 0) {
                fehler++;
                System.out.println("Konflikt " + k + " bei " + zx + " " + zy + " " + zz + " erste " + erste);
            }
            // Mittelspur zusammenhängend, höchstens ein Block hinunter je Stufe
            int px = 0, py = 0, pz = 0;
            for (var s : plan) {
                if (Math.abs(s.x() - px) + Math.abs(s.z() - pz) != 1 || py - s.y() > 1 || py - s.y() < 0) {
                    fehler++;
                    System.out.println("Sprung bei " + zx + " " + zy + " " + zz);
                    break;
                }
                px = s.x(); py = s.y(); pz = s.z();
            }
            laengste = Math.max(laengste, plan.size());
        }
        System.out.println(faelle + " Fälle, " + fehler + " Fehler, " + leer + " ohne Plan, längste " + laengste + " Stufen");
    }
}
