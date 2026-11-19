import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;

public class EcosystemApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}

/* ===================== ПАРАМЕТРЫ МОДЕЛИ ===================== */
class Params {
    int width = 800, height = 600;

    // Начальные условия (применяются при сбросе)
    int initPlants = 150;
    int initHerbivores = 30;
    int initPredators = 8;

    // Рост растений (штук за тик в среднем)
    double plantGrowthRate = 1.5;

    // Травоядные
    double herbSpeed = 2.0;
    double herbDetect = 100;      // радиус обнаружения еды
    double herbEatRadius = 6;     // радиус поедания
    double herbStartEnergy = 40;
    double herbEatGain = 25;
    double herbMetabolic = 0.8;   // расход энергии за тик
    double herbReproEnergy = 80;  // порог для размножения
    int herbMaxAge = 600;

    // Хищники
    double predSpeed = 2.5;
    double predDetect = 120;
    double predEatRadius = 8;
    double predStartEnergy = 50;
    double predEatGain = 45;
    double predMetabolic = 1.0;
    double predReproEnergy = 100;
    int predMaxAge = 900;
}

/* ===================== ОРГАНИЗМЫ ===================== */
abstract class Organism {
    static final Random rnd = new Random();
    double x, y, energy;
    int age = 0;
    boolean alive = true;
    Params p;

    Organism(Params p) {
        this.p = p;
        this.x = rnd.nextDouble() * p.width;
        this.y = rnd.nextDouble() * p.height;
    }

    Organism(Params p, double x, double y, double energy) {
        this.p = p;
        this.x = x;
        this.y = y;
        this.energy = energy;
    }

    void wander(double speed) {
        double a = rnd.nextDouble() * 2 * Math.PI;
        x = clamp(x + Math.cos(a) * speed, 0, p.width);
        y = clamp(y + Math.sin(a) * speed, 0, p.height);
    }

    void moveToward(double tx, double ty, double speed) {
        double dx = tx - x, dy = ty - y;
        double d = Math.sqrt(dx * dx + dy * dy);
        if (d < 1e-9) return;
        x = clamp(x + dx / d * speed, 0, p.width);
        y = clamp(y + dy / d * speed, 0, p.height);
    }

    static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}

class Plant extends Organism {
    Plant(Params p) { super(p); energy = 0; }
}

class Herbivore extends Organism {
    Herbivore(Params p) { super(p); energy = p.herbStartEnergy; }
    Herbivore(Params p, double x, double y, double e) { super(p, x, y, e); }

    Herbivore step(World w) {
        age++;

        // найти ближайшее растение
        Plant target = null;
        double bestD = p.herbDetect * p.herbDetect;
        for (Plant pl : w.plants) {
            double dx = pl.x - x, dy = pl.y - y;
            double d = dx * dx + dy * dy;
            if (d < bestD) { bestD = d; target = pl; }
        }

        if (target != null) {
            double dist = Math.sqrt(bestD);
            if (dist <= p.herbEatRadius) {
                if (w.plants.remove(target)) energy += p.herbEatGain;
            } else {
                moveToward(target.x, target.y, p.herbSpeed);
                energy -= p.herbMetabolic;
            }
        } else {
            wander(p.herbSpeed);
            energy -= p.herbMetabolic;
        }

        if (energy <= 0 || age > p.herbMaxAge) { alive = false; return null; }

        Herbivore child = null;
        if (energy >= p.herbReproEnergy) {
            energy /= 2;
            child = new Herbivore(p, x + (rnd.nextDouble() - 0.5) * 10,
                                     y + (rnd.nextDouble() - 0.5) * 10, energy);
        }
        return child;
    }
}

class Predator extends Organism {
    Predator(Params p) { super(p); energy = p.predStartEnergy; }
    Predator(Params p, double x, double y, double e) { super(p, x, y, e); }

    Predator step(World w) {
        age++;

        Herbivore target = null;
        double bestD = p.predDetect * p.predDetect;
        for (Herbivore h : w.herbivores) {
            double dx = h.x - x, dy = h.y - y;
            double d = dx * dx + dy * dy;
            if (d < bestD) { bestD = d; target = h; }
        }

        if (target != null) {
            double dist = Math.sqrt(bestD);
            if (dist <= p.predEatRadius) {
                if (w.herbivores.remove(target)) energy += p.predEatGain;
            } else {
                moveToward(target.x, target.y, p.predSpeed);
                energy -= p.predMetabolic;
            }
        } else {
            wander(p.predSpeed);
            energy -= p.predMetabolic;
        }

        if (energy <= 0 || age > p.predMaxAge) { alive = false; return null; }

        Predator child = null;
        if (energy >= p.predReproEnergy) {
            energy /= 2;
            child = new Predator(p, x + (rnd.nextDouble() - 0.5) * 10,
                                    y + (rnd.nextDouble() - 0.5) * 10, energy);
        }
        return child;
    }
}

/* ===================== МИР ===================== */
class World {
    Params p;
    List<Plant> plants = new ArrayList<>();
    List<Herbivore> herbivores = new ArrayList<>();
    List<Predator> predators = new ArrayList<>();

    World(Params p) { this.p = p; reset(); }

    void reset() {
        plants.clear();
        herbivores.clear();
        predators.clear();
        for (int i = 0; i < p.initPlants; i++) plants.add(new Plant(p));
        for (int i = 0; i < p.initHerbivores; i++) herbivores.add(new Herbivore(p));
        for (int i = 0; i < p.initPredators; i++) predators.add(new Predator(p));
    }

    void step() {
        // рост растений
        double g = p.plantGrowthRate;
        int n = (int) g;
        if (Math.random() < g - n) n++;
        for (int i = 0; i < n; i++) plants.add(new Plant(p));

        // травоядные
        List<Herbivore> childH = new ArrayList<>();
        Iterator<Herbivore> ih = herbivores.iterator();
        while (ih.hasNext()) {
            Herbivore h = ih.next();
            Herbivore c = h.step(this);
            if (!h.alive) ih.remove();
            if (c != null) childH.add(c);
        }
        herbivores.addAll(childH);

        // хищники
        List<Predator> childP = new ArrayList<>();
        Iterator<Predator> ip = predators.iterator();
        while (ip.hasNext()) {
            Predator pr = ip.next();
            Predator c = pr.step(this);
            if (!pr.alive) ip.remove();
            if (c != null) childP.add(c);
        }
        predators.addAll(childP);
    }
}

/* ===================== ПАНЕЛЬ ВИЗУАЛИЗАЦИИ ===================== */
class SimPanel extends JPanel {
    World world;

    SimPanel(World world) {
        this.world = world;
        setBackground(new Color(235, 245, 225));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        double scale = Math.min(getWidth() / (double) world.p.width,
                                getHeight() / (double) world.p.height);
        double offX = (getWidth() - world.p.width * scale) / 2;
        double offY = (getHeight() - world.p.height * scale) / 2;

        g2.setColor(new Color(40, 150, 40));
        for (Plant pl : world.plants) {
            int px = (int) (offX + pl.x * scale);
            int py = (int) (offY + pl.y * scale);
            g2.fillOval(px - 2, py - 2, 5, 5);
        }

        g2.setColor(new Color(60, 120, 220));
        for (Herbivore h : world.herbivores) {
            int px = (int) (offX + h.x * scale);
            int py = (int) (offY + h.y * scale);
            g2.fillOval(px - 4, py - 4, 9, 9);
        }

        g2.setColor(new Color(200, 40, 40));
        for (Predator pr : world.predators) {
            int px = (int) (offX + pr.x * scale);
            int py = (int) (offY + pr.y * scale);
            g2.fillOval(px - 5, py - 5, 11, 11);
        }

        g2.setColor(Color.BLACK);
        g2.setFont(new Font("SansSerif", Font.BOLD, 13));
        g2.drawString("Растения: " + world.plants.size(), 10, 20);
        g2.drawString("Травоядные: " + world.herbivores.size(), 10, 40);
        g2.drawString("Хищники: " + world.predators.size(), 10, 60);
    }
}

/* ===================== ГРАФИКИ ===================== */
class ChartPanel extends JPanel {
    List<int[]> history;

    ChartPanel(List<int[]> history) {
        this.history = history;
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createLineBorder(Color.GRAY));
    }

    @Override
    public Dimension getPreferredSize() { return new Dimension(0, 220); }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth(), h = getHeight();
        int padL = 50, padR = 110, padT = 15, padB = 15;
        int gw = w - padL - padR;
        int gh = h - padT - padB;
        if (gw <= 0 || gh <= 0) return;

        g2.setColor(Color.DARK_GRAY);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g2.drawString("Численность видов во времени", padL, 11);

        if (history.isEmpty()) return;

        int max = 10;
        for (int[] r : history)
            max = Math.max(max, Math.max(r[0], Math.max(r[1], r[2])));
        max = ((max + 9) / 10) * 10;

        g2.setColor(new Color(225, 225, 225));
        for (int i = 0; i <= 4; i++) {
            int y = padT + gh - (int) (gh * i / 4.0);
            g2.drawLine(padL, y, padL + gw, y);
        }
        g2.setColor(Color.GRAY);
        g2.drawLine(padL, padT, padL, padT + gh);
        g2.drawLine(padL, padT + gh, padL + gw, padT + gh);

        g2.setColor(Color.DARK_GRAY);
        for (int i = 0; i <= 4; i++) {
            int y = padT + gh - (int) (gh * i / 4.0);
            g2.drawString(String.valueOf(max * i / 4), 8, y + 4);
        }

        drawSeries(g2, 0, new Color(40, 150, 40), gw, gh, padL, padT, max);
        drawSeries(g2, 1, new Color(60, 120, 220), gw, gh, padL, padT, max);
        drawSeries(g2, 2, new Color(200, 40, 40), gw, gh, padL, padT, max);

        int lx = padL + gw + 15, ly = padT + 20;
        g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        drawLegend(g2, lx, ly, new Color(40, 150, 40), "Растения");
        drawLegend(g2, lx, ly + 20, new Color(60, 120, 220), "Травоядные");
        drawLegend(g2, lx, ly + 40, new Color(200, 40, 40), "Хищники");
    }

    private void drawSeries(Graphics2D g2, int idx, Color c, int gw, int gh,
                            int padL, int padT, int max) {
        g2.setColor(c);
        g2.setStroke(new BasicStroke(1.5f));
        int n = history.size();
        if (n < 2) return;
        int px = -1, py = -1;
        for (int i = 0; i < n; i++) {
            int x = padL + (int) ((double) i / (n - 1) * gw);
            int y = padT + gh - (int) ((double) history.get(i)[idx] / max * gh);
            if (px >= 0) g2.drawLine(px, py, x, y);
            px = x; py = y;
        }
    }

    private void drawLegend(Graphics2D g2, int x, int y, Color c, String txt) {
        g2.setColor(c);
        g2.fillRect(x, y - 8, 12, 12);
        g2.setColor(Color.DARK_GRAY);
        g2.drawString(txt, x + 18, y + 2);
    }
}

/* ===================== ОКНО ПРИЛОЖЕНИЯ ===================== */
interface IntConsumer2 { void accept(int v); }
interface DoubleConsumer2 { void accept(double v); }

class MainFrame extends JFrame {
    final Params params = new Params();
    final World world = new World(params);
    final List<int[]> history = new ArrayList<>();

    SimPanel simPanel;
    ChartPanel chartPanel;
    Timer timer;
    int tick = 0;
    JLabel statusLabel;

    MainFrame() {
        setTitle("Жизнь в миниатюре — моделирование экосистемы");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1250, 850);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(5, 5));

        timer = new Timer(100, e -> onTick());
        simPanel = new SimPanel(world);
        chartPanel = new ChartPanel(history);

        JScrollPane leftScroll = new JScrollPane(buildLeftPanel());
        leftScroll.setPreferredSize(new Dimension(340, 0));
        add(leftScroll, BorderLayout.WEST);
        add(simPanel, BorderLayout.CENTER);
        add(chartPanel, BorderLayout.SOUTH);
    }

    private JPanel buildLeftPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Кнопки управления
        JPanel btns = new JPanel(new GridLayout(1, 3, 5, 5));
        JButton startBtn = new JButton("Старт");
        JButton pauseBtn = new JButton("Пауза");
        JButton resetBtn = new JButton("Сброс");
        btns.add(startBtn); btns.add(pauseBtn); btns.add(resetBtn);
        btns.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        panel.add(btns);
        panel.add(Box.createVerticalStrut(10));

        startBtn.addActionListener(e -> { timer.start(); updateStatus("Симуляция идёт"); });
        pauseBtn.addActionListener(e -> { timer.stop(); updateStatus("Пауза"); });
        resetBtn.addActionListener(e -> resetSim());

        // Скорость
        JPanel speedP = new JPanel(new BorderLayout(5, 5));
        speedP.setBorder(BorderFactory.createTitledBorder("Скорость симуляции"));
        JSlider speed = new JSlider(1, 60, 10);
        JLabel speedVal = new JLabel("10 тик/с", SwingConstants.CENTER);
        speed.addChangeListener(e -> {
            timer.setDelay(1000 / Math.max(1, speed.getValue()));
            speedVal.setText(speed.getValue() + " тик/с");
        });
        speedP.add(speed, BorderLayout.CENTER);
        speedP.add(speedVal, BorderLayout.SOUTH);
        speedP.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        panel.add(speedP);
        panel.add(Box.createVerticalStrut(10));

        // Начальные условия
        JPanel initP = new JPanel(new GridLayout(0, 2, 5, 5));
        initP.setBorder(BorderFactory.createTitledBorder("Начальные условия (при сбросе)"));
        initP.add(new JLabel("Растения:"));
        initP.add(spinner(0, 2000, params.initPlants, v -> params.initPlants = v));
        initP.add(new JLabel("Травоядные:"));
        initP.add(spinner(0, 1000, params.initHerbivores, v -> params.initHerbivores = v));
        initP.add(new JLabel("Хищники:"));
        initP.add(spinner(0, 500, params.initPredators, v -> params.initPredators = v));
        initP.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));
        panel.add(initP);
        panel.add(Box.createVerticalStrut(10));

        // Параметры модели
        JPanel liveP = new JPanel(new GridLayout(0, 2, 5, 5));
        liveP.setBorder(BorderFactory.createTitledBorder("Параметры модели (live)"));
        liveP.add(new JLabel("Рост растений (шт/тик):"));
        liveP.add(dspin(0, 50, params.plantGrowthRate, 0.1, v -> params.plantGrowthRate = v));
        liveP.add(new JLabel("Скорость травоядных:"));
        liveP.add(dspin(0.1, 10, params.herbSpeed, 0.1, v -> params.herbSpeed = v));
        liveP.add(new JLabel("Энергия от съеденного растения:"));
        liveP.add(dspin(1, 200, params.herbEatGain, 1, v -> params.herbEatGain = v));
        liveP.add(new JLabel("Порог размножения травоядных:"));
        liveP.add(dspin(10, 500, params.herbReproEnergy, 5, v -> params.herbReproEnergy = v));
        liveP.add(new JLabel("Жизнь травоядных (тиков):"));
        liveP.add(spinner(10, 10000, params.herbMaxAge, v -> params.herbMaxAge = v));
        liveP.add(new JLabel("Скорость хищников:"));
        liveP.add(dspin(0.1, 10, params.predSpeed, 0.1, v -> params.predSpeed = v));
        liveP.add(new JLabel("Энергия от съеденной жертвы:"));
        liveP.add(dspin(1, 200, params.predEatGain, 1, v -> params.predEatGain = v));
        liveP.add(new JLabel("Порог размножения хищников:"));
        liveP.add(dspin(10, 500, params.predReproEnergy, 5, v -> params.predReproEnergy = v));
        liveP.add(new JLabel("Жизнь хищников (тиков):"));
        liveP.add(spinner(10, 10000, params.predMaxAge, v -> params.predMaxAge = v));
        liveP.setMaximumSize(new Dimension(Integer.MAX_VALUE, 330));
        panel.add(liveP);
        panel.add(Box.createVerticalStrut(10));

        // Сохранение
        JButton saveBtn = new JButton("Сохранить результаты (CSV)");
        saveBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        saveBtn.addActionListener(e -> saveResults());
        panel.add(saveBtn);

        panel.add(Box.createVerticalStrut(10));
        statusLabel = new JLabel("Тиков: 0");
        panel.add(statusLabel);

        return panel;
    }

    private JSpinner spinner(int min, int max, int val, IntConsumer2 c) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(val, min, max, 1));
        s.addChangeListener(e -> c.accept((Integer) s.getValue()));
        return s;
    }

    private JSpinner dspin(double min, double max, double val, double step, DoubleConsumer2 c) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(val, min, max, step));
        s.addChangeListener(e -> c.accept((Double) s.getValue()));
        return s;
    }

    private void resetSim() {
        timer.stop();
        world.reset();
        history.clear();
        tick = 0;
        updateStatus("Сброс");
        simPanel.repaint();
        chartPanel.repaint();
    }

    private void onTick() {
        world.step();
        tick++;
        history.add(new int[]{world.plants.size(), world.herbivores.size(), world.predators.size()});
        if (history.size() > 600) history.remove(0);

        updateStatus("Симуляция идёт");
        simPanel.repaint();
        chartPanel.repaint();
    }

    private void updateStatus(String state) {
        statusLabel.setText(state + " | Тиков: " + tick);
    }

    private void saveResults() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("ecosystem_results.csv"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (PrintWriter pw = new PrintWriter(fc.getSelectedFile())) {
                pw.println("tick,plants,herbivores,predators");
                int startTick = Math.max(0, tick - history.size());
                for (int i = 0; i < history.size(); i++) {
                    int[] r = history.get(i);
                    pw.println((startTick + i) + "," + r[0] + "," + r[1] + "," + r[2]);
                }
                JOptionPane.showMessageDialog(this,
                        "Сохранено: " + fc.getSelectedFile().getAbsolutePath(),
                        "Готово", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Ошибка сохранения: " + ex.getMessage(),
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}