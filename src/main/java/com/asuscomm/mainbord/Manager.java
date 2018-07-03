package com.asuscomm.mainbord;


import lombok.Getter;
import lombok.extern.log4j.Log4j;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.asuscomm.mainbord.Manager.RomCommands.*;

@Log4j
public class Manager {

    private final Map<Double, String> tempCelcFromBinary = new TreeMap<>();

    // <celsius, binary>
    private final Map<RomCommands, String> commands = new TreeMap<>();
    private final int PAUSE = 10; // пауза между сигналами в милисекундах
    private final String pin;
    private final String fileName = "input.txt";

    public String getPin() {
        return pin;
    }

    public String getFileName() {
        return fileName;
    }


    private Set<String> inValue = new HashSet<>();

    Manager(String pin) throws InterruptedException {
        this.pin = pin;

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

        commands.put(SEARCH_ROM, "F0h"); // запускается в начале, спрашивает номер датчика
        commands.put(READ_ROM, "33h"); // This command allows the bus master to read the DS18B20’s 8-bit family code, unique 48-bit serial number, and 8-bit CRC. This command can only be used if there is a single DS18B20 on the bus
        commands.put(MATCH_ROM, "55H");
        commands.put(SKIP_ROM, "CCh"); // This command can save time in a single drop bus system by allowing the bus master to access the memory functions without providing the 64-bit ROM code.
        commands.put(ALARM_SEARCH, "ECh"); // The operation of this command is identical to the operation of the Search ROM command except that only slaves with a set alarm flag will respond
        commands.put(CONVERT_T, "44h"); // Initiates temperature conversion. DS18B20 transmits conversion status to master (not applicable for parasite-powered DS18B20s).
        commands.put(WRITE_SCRATCHPAD, "4Eh"); // Writes data into scratchpad bytes 2, 3, and 4 (T H , T L , and configuration registers). Master transmits 3 data bytes to DS18B20.
        commands.put(READ_SCRATCHPAD, "BEh"); // Reads the entire scratchpad including the CRC byte. DS18B20 transmits up to 9 data bytes to master.
        commands.put(COPY_SCRATCHPAD, "48h"); // Copies T H , T L , and configuration register data from the scratchpad to EEPROM.
        commands.put(RECALL_E2, "B8h"); // Recalls T H , T L , and configuration register data from EEPROM to the scratchpad. DS18B20 transmits recall status to master.
        commands.put(READ_POWER_SUPPLY, "B4h"); // Signals DS18B20 power supply mode to the master. DS18B20 transmits supply status to master.
        log.trace("__ init starting");

//        Files.write(Paths.get(" /sys/class/gpio/export"), "35".getBytes());


        try {
            Process process;
            process = Runtime.getRuntime()
                    .exec("echo 35 > /sys/class/gpio/export");
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            log.error(e);
        }

        // читаем температуру 3 раза
        for (int i = 0; i < 3; i++) {
            init();
            sendHexCommand(commands.get(CONVERT_T));
            sendHexCommand(commands.get(SKIP_ROM));
            sendHexCommand(commands.get(READ_SCRATCHPAD));
            String temperature = readNBits(3*8);
            System.out.println(temperature);
        }
    }

    enum RomCommands {
        SEARCH_ROM, READ_ROM, MATCH_ROM, SKIP_ROM, ALARM_SEARCH,
        CONVERT_T, WRITE_SCRATCHPAD, READ_SCRATCHPAD, COPY_SCRATCHPAD,
        RECALL_E2, READ_POWER_SUPPLY
    }

    /**
     * The transaction sequence for accessing the DS18B20 is
     * as follows:
     * <p>
     * Step 1. Initialization
     * Step 2. ROM Command (followed by any required data exchange)
     * Step 3. DS18B20 Function Command (followed by any required data exchange)
     */
    private void init() {
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
    private String initialization() {
        // Передаём 0 как минимум 480 мс
        prepareToWrite();
        writeLowVoltage(500);

        // Передаём 1 не больше чем 15мс, чтобы успеть переключиться в режим чтения
        writeHighVoltage(10);

        log.trace("__ write succesfull");
        try {
            String filePath = "/home/pi" + File.separator + fileName;
            Files.deleteIfExists(Paths.get(filePath));
            Files.createFile(Paths.get(filePath));
            log.trace("__ file created");
            try (PrintWriter fw = new PrintWriter(filePath);
                 BufferedReader br = new BufferedReader(new FileReader("/sys/class/gpio/" + "gpio35" + "/" + "value"))) {
                prepareToRead();
                log.trace("__ ready to read");

                File file = new File("/sys/class/gpio/" + "gpio35" + "/" + "value");

//                String line = "0";
                int count = 0;
                Date dateStart = new Date();
                Date dateEnd = new Date();
                long lm = 0;

                List<Pair> timeVoltageList = new LinkedList<>();
                while (count != 1000) {
                    Thread.sleep(1);
//                    timeVoltageList.add(new Pair((dateEnd.getTime() - dateStart.getTime()), Integer.valueOf(line)));
                    dateStart = new Date();
//                    br.mark(0);
//                    br.reset();
                    long currentLM = file.lastModified();
                    if (currentLM == lm) {
                        timeVoltageList.add(new Pair((dateEnd.getTime() - dateStart.getTime()), -1));
                        count++;
                        dateEnd = new Date();
                        continue;
                    } else {
                        timeVoltageList.add(new Pair((dateEnd.getTime() - dateStart.getTime()), readVoltage()));
                    }


//                    line = br.readLine();
                    count++;
                    dateEnd = new Date();
                }

/*                List<Pair> timeVoltageList = new LinkedList<>();
                for (int i = 0; i < 1000; i++) {
                    Date dateStart = new Date();
                    Date dateEnd = new Date();
                    dateStart = new Date();
                    int val = readVoltage();

                    FileReader reader = new FileReader("");
                    reader.read();
                    reader.ready();

                    dateEnd = new Date();
                    timeVoltageList.add(new Pair((dateEnd.getTime() - dateStart.getTime()), val));
//                    log.trace("Time read in milliseconds(Files.readAllLines): " + (dateEnd.getTime() - dateStart.getTime()) + ", value = " + val);
                }
                */
                fw.print(timeVoltageList.toString());
                fw.flush();
            }
        } catch (Exception e) {
            log.error(e);
        }
        log.trace("__ read succesfull");
        return "presence pulses";
    }

    @Getter
    class Pair {
        long time;
        int voltage;

        public Pair(long time, int voltage) {
            this.time = time;
            this.voltage = voltage;
        }

        @Override
        public String toString() {
            return "(" + time + "," + voltage + ")";
        }
    }

    private String stringCompleteTo12(String in) {
        StringBuilder sb = new StringBuilder(in);
        for (int i = 0; i < 12 - in.length(); i++) {
            sb.insert(0, "0");
        }
        return sb.toString();
    }

    public static void main(String[] args) throws InterruptedException {
/*        // измеряем скорость чтения и записи в драйвер gpio
        // 1) постоянно открываем файл
        // через flush не создавая reader и writer

        try {
            Process process;
            process = Runtime.getRuntime()
                    .exec("echo 35 > /sys/class/gpio/export");
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            log.error(e);
        }

        try {
            Date dateStart = new Date();
            Date dateEnd = new Date();

            Files.write(Paths.get("/sys/class/gpio/" + "gpio35" + "/" + "direction"), "in".getBytes());
            for (int i = 0; i < 3; i++) {
                dateStart = new Date();
                List<String> voltages = Files.readAllLines(Paths.get("/sys/class/gpio/" + "gpio35" + "/" + "value"));
                Integer value = Integer.valueOf(voltages.get(0));
                dateEnd = new Date();
                System.out.println("Time read in milliseconds(Files.readAllLines): " + (dateEnd.getTime() - dateStart.getTime()));
            }

            Files.write(Paths.get("/sys/class/gpio/" + "gpio35" + "/" + "direction"), "out".getBytes());
            for (int i = 0; i < 3; i++) {
                dateStart = new Date();
                Files.write(Paths.get("/sys/class/gpio/" + "gpio35" + "/" + "value"), String.valueOf(1).getBytes());
                dateEnd = new Date();
                System.out.println("Time write in milliseconds(Files.write): " + (dateEnd.getTime() - dateStart.getTime()));
            }
        } catch (Exception e) {
            log.error(e);
        }*/
        Manager manager = new Manager(BpiM2uPin.pins.get("pin7"));
/*        StringBuilder sb = new StringBuilder();
            sb.append(manager.readVoltage());
            System.out.println(sb);
            manager.writeVoltage(1);
            TimeUnit.SECONDS.sleep(1);
            manager.writeVoltage(0);
            TimeUnit.SECONDS.sleep(1);
            manager.writeVoltage(1);
            TimeUnit.SECONDS.sleep(1);
        manager.writeVoltage(0);*/
    }

    private void prepareToWrite() {
        try {
            Files.write(Paths.get("/sys/class/gpio/" + pin + "/" + "direction"), "out".getBytes());
        } catch (Exception e) {
            log.error(e);
        }
    }

    private void prepareToRead() {
        try {
            Files.write(Paths.get("/sys/class/gpio/" + pin + "/" + "direction"), "in".getBytes());
        } catch (Exception e) {
            log.error(e);
        }
    }

    private void writeVoltage(int value) {
        try {
            Files.write(Paths.get("/sys/class/gpio/" + pin + "/" + "value"), String.valueOf(value).getBytes());
        } catch (IOException e) {
            log.error(e);
        }
    }

    private int readVoltage() {
        try {
            List<String> voltages = Files.readAllLines(Paths.get("/sys/class/gpio/" + pin + "/" + "value"));
            return Integer.valueOf(voltages.get(0));
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException(e.getMessage());
        }
    }

    private void writeHex(String hex) {
        for (int i = 0; i < hex.length(); i++) {
            try {
                writeVoltage((int) hex.charAt(i));
                TimeUnit.MILLISECONDS.sleep(PAUSE);
            } catch (InterruptedException e) {
                log.error(e);
            }
        }
    }

    private void writeHighVoltage(int milSec) {
        try {
            writeVoltage(1);
            TimeUnit.MILLISECONDS.sleep(milSec);
        } catch (Exception e) {
            log.error(e);
        }
    }

    private void writeLowVoltage(int milSec) {
        try {
            writeVoltage(0);
            TimeUnit.MILLISECONDS.sleep(milSec);
        } catch (Exception e) {
            log.error(e);
        }
    }

    private final int READ_TIME_SLOT = 60; //microseconds

    private void sendHexCommand(String hexCommand) throws InterruptedException {

        int WRITE_ONE_TIME_SLOT_AFTER_PULLING_THE_1_WIRE_BUS_LOW = 15;
        int RECOVERY_TIME_BETWEEN_INDIVIDUAL_WRITE_SLOTS = 1;
        int WRITE_TIME_SLOT = 60;


        String binariCommand = hexToBin(hexCommand);
        int prevBit = 0;
        writeLowVoltage(WRITE_ONE_TIME_SLOT_AFTER_PULLING_THE_1_WIRE_BUS_LOW);
        for (int i = 0; i < binariCommand.length(); i++) {
            char bit = binariCommand.charAt(i);
            writeLowVoltage(RECOVERY_TIME_BETWEEN_INDIVIDUAL_WRITE_SLOTS);
            if (bit == 1){
                writeHighVoltage(WRITE_TIME_SLOT);
            }
            else {
                writeLowVoltage(WRITE_TIME_SLOT);
            }
/*            if (prevBit == 1 && bit == 1) {
                writeLowVoltage(RECOVERY_TIME_BETWEEN_INDIVIDUAL_WRITE_SLOTS);
                writeHighVoltage(WRITE_TIME_SLOT);
            } else if (prevBit == 0 && bit == 1) {
                writeLowVoltage(WRITE_ONE_TIME_SLOT_AFTER_PULLING_THE_1_WIRE_BUS_LOW);
                writeHighVoltage(WRITE_TIME_SLOT);
            } else if (prevBit == 1 && bit == 0) {

            } else if (prevBit == 0 && bit == 0) {
                writeLowVoltage(WRITE_TIME_SLOT);
            }
            prevBit = bit;*/
        }
    }

    private int readWithLock(){
        return 0;
    }

    private String readNBits (int numberOfBits){
        StringBuilder sb = new StringBuilder();
        writeLowVoltage(15);
        for (int i = 0; i < numberOfBits; i++) {
            sb.append(readWithLock());
        }
        return sb.toString();
    }

    private String hexToBin(String s) {
        return new BigInteger(s, 16).toString(2);
    }
}
