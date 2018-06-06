package com.asuscomm.mainbord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Manager {

    private final Map<Double, String> tempCelcFromBinary = new TreeMap<>();

    // <celsius, binary>
    private final Map<String, String> commands = new TreeMap<>();
    private final int PAUSE = 10; // пауза между сигналами в милисекундах

    private static final String pin7 = "gpio35";
    private static final String pin11 = "gpio276";

    private Set<String> inValue = new HashSet<>();

    Manager() throws IOException, InterruptedException {
        //creating celsius to binary from 0 to 125
        double sum = 0;
        for (int i = 0; i < 201; i++) {
            tempCelcFromBinary.put(sum, stringCompleteTo12(Integer.toBinaryString(i)));
            sum = sum + 0.0625;
        }
        //creating celsius to binary from -55 to -0.625
        sum = -55;
        for (int i = 64656; i <= 65535; i++) {
            tempCelcFromBinary.put(sum, stringCompleteTo12(Integer.toBinaryString(i)));
            sum = sum + 0.0625;
        }

        commands.put("F0h", "Search Rom"); // запускается в начале, спрашивает номер датчика
        commands.put("33h", "Read Rom");
        commands.put("55H", "Match Rom");
        commands.put("CCh", "Skip Rom");
        commands.put("ECh", "Alarm Search"); // The operation of this command is identical to the operation of the Search ROM command except that only slaves with a set alarm flag will respond
        commands.put("44h", "Convert T");
        commands.put("4Eh", "Write Scratchpad");
        commands.put("BEh", "Read Scratchpad");
        commands.put("48h", "Copy Scratchpad");
        commands.put("B8h", "Recall E2");
        commands.put("B4h", "Read Power Supply");

        init();
    }

    /**
     * The transaction sequence for accessing the DS18B20 is
     * as follows:
     * <p>
     * Step 1. Initialization
     * Step 2. ROM Command (followed by any required data exchange)
     * Step 3. DS18B20 Function Command (followed by any required data exchange)
     */
    private void init() throws IOException, InterruptedException {
        String sensorNumber = initialization();
    }

    /**
     * All communication with the DS18B20 begins with an ini-
     * tialization sequence that consists of a reset pulse from the
     * master followed by a presence pulse from the DS18B20.
     * This is illustrated in Figure 15. When the DS18B20 sends
     * the presence pulse in response to the reset, it is indicating
     * to the master that it is on the bus and ready to operate.
     */
    private String initialization() throws IOException, InterruptedException {
        // Передаём 0 как минимум 480 мс
        writeLowVoltage(pin7, 500);

        // Передаём 1 не больше чем 15мс
        writeHighVoltage(pin7, 15);

        int result = readVoltage(pin7);
        return String.valueOf(result);
    }

    private String stringCompleteTo12(String in) {
        StringBuilder sb = new StringBuilder(in);
        for (int i = 0; i < 12 - in.length(); i++) {
            sb.insert(0, "0");
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        try {
            Manager manager = new Manager();
            StringBuilder sb = new StringBuilder();
            sb.append(manager.readVoltage(pin7));
            System.out.println(sb);
            manager.writeVoltage(pin7, 1);
            TimeUnit.SECONDS.sleep(1);
            manager.writeVoltage(pin7, 0);
            TimeUnit.SECONDS.sleep(1);
            manager.writeVoltage(pin7, 1);
            TimeUnit.SECONDS.sleep(1);
            manager.writeVoltage(pin7, 0);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        } catch (InterruptedException e) {
            System.out.println(e.getMessage());
        }
    }

    private void writeVoltage(String pin, int value) throws IOException {
        Files.write(Paths.get("/sys/class/gpio/" + pin + "/" + "direction"), "out".getBytes());
        Files.write(Paths.get("/sys/class/gpio/" + pin + "/" + "value"), String.valueOf(value).getBytes());
    }

    private int readVoltage(String pin) throws IOException {
        Files.write(Paths.get("/sys/class/gpio/" + pin + "/" + "direction"), "in".getBytes());
        List<String> voltages = Files.readAllLines(Paths.get("/sys/class/gpio/" + pin + "/" + "value"));
        return Integer.valueOf(voltages.get(0));
    }

    private void writeHex(String pin, String hex) throws InterruptedException, IOException {
        for (int i = 0; i < hex.length(); i++) {
            writeVoltage(pin, (int) hex.charAt(i));
            TimeUnit.MILLISECONDS.sleep(PAUSE);
        }
    }

    private void writeHighVoltage(String pin, int milSec) throws IOException, InterruptedException {
        writeVoltage(pin, 1);
        TimeUnit.MILLISECONDS.sleep(milSec);
    }

    private void writeLowVoltage(String pin, int milSec) throws IOException, InterruptedException {
        writeVoltage(pin, 0);
        TimeUnit.MILLISECONDS.sleep(milSec);
    }
}
