/* SPDX-License-Identifier: MIT */
/**
 * Read-only battery bindings from stock monitor/getter relationships.
 * Instruction recipes validate the model layout; all callable and data
 * addresses come from live call targets and literal loads.
 */
final class BatteryBindings {
    private BatteryBindings() {}
    private static final RomRecipe CAPACITY = BatteryPatterns.BATTERY_CAPACITY();
    private static final RomRecipe BARS = BatteryPatterns.BATTERY_BARS();
    private static final RomRecipe VOLTAGE = BatteryPatterns.BATTERY_VOLTAGE();
    private static final RomRecipe NOMINAL = BatteryPatterns.BATTERY_NOMINAL();
    private static final RomRecipe CHARGER = BatteryPatterns.BATTERY_CHARGER();
    private static final RomRecipe STATUS = BatteryPatterns.BATTERY_STATUS();
    private static final RomRecipe RESUME = BatteryPatterns.BATTERY_RESUME();
    private static final RomRecipe ENERGY = BatteryPatterns.BATTERY_ENERGY();
    private static final RomRecipe DELTA = BatteryPatterns.BATTERY_DELTA();
    private static final RomRecipe BARS_FORWARD = BatteryPatterns.BATTERY_BARS_FORWARD();
    private static final RomRecipe VOLTAGE_FORWARD = BatteryPatterns.BATTERY_VOLTAGE_FORWARD();
    private static final RomRecipe RESUME_FORWARD = BatteryPatterns.BATTERY_RESUME_FORWARD();
    private static final RomRecipe STATUS_FORWARD = BatteryPatterns.BATTERY_STATUS_FORWARD();
    private static final RomRecipe ENERGY_FORWARD = BatteryPatterns.BATTERY_ENERGY_FORWARD();
    private static final RomRecipe COMPLETE = BatteryPatterns.BATTERY_COMPLETE();
    private static final RomSearch ROOT = new RomSearch(new RomRecipe[] {CAPACITY});

    /** Two observed code layouts; selection follows matched instructions only. */
    private static final class Model {
        final RomRecipe bars, voltage, resume, status, energy;
        final int deltaCall, energyCall, barsBaseBias, voltageBaseBias;
        final int[] estimateLoads;
        Model(RomRecipe barsRecipe, RomRecipe voltageRecipe, RomRecipe resumeRecipe,
                RomRecipe statusRecipe, RomRecipe energyRecipe, int deltaOffset, int energyOffset,
                int barsBias, int voltageBias, int[] loads) {
            bars = barsRecipe;
            voltage = voltageRecipe;
            resume = resumeRecipe;
            status = statusRecipe;
            energy = energyRecipe;
            deltaCall = deltaOffset;
            energyCall = energyOffset;
            barsBaseBias = barsBias;
            voltageBaseBias = voltageBias;
            estimateLoads = loads;
        }
    }
    private static final Model[] MODELS = {new Model(BARS, VOLTAGE, RESUME, STATUS, ENERGY, 114,
                                                   144, -28, 0, new int[] {58, 162, 172, 232}),
            new Model(BARS_FORWARD, VOLTAGE_FORWARD, RESUME_FORWARD, STATUS_FORWARD, ENERGY_FORWARD,
                    116, 204, 32, 60, new int[] {58, 222, 232, 298})};

    static void prepare() {}

    static boolean bind(FirmwareResolver r) {
        if (!"RM-443".equals(r.familyId()) || !ROOT.resolve(r))
            return false;
        int capacity = ROOT.address(0);
        int nominal = r.thumbCall(capacity + 22);
        if (nominal != r.thumbCall(capacity + 30) || !r.matches(nominal & ~1, NOMINAL))
            return false;
        Model modelLayout = null;
        int resume = 0;
        for (int i = 0; i < MODELS.length; i++) {
            int candidate = r.findUnique(MODELS[i].resume, capacity - 4096, 8192);
            if (candidate != 0) {
                if (modelLayout != null)
                    return false;
                modelLayout = MODELS[i];
                resume = candidate;
            }
        }
        if (modelLayout == null)
            return false;
        int bars = r.findUnique(modelLayout.bars, capacity - 128, 256);
        int voltage = r.findUnique(modelLayout.voltage, capacity - 128, 256);
        int status = r.findUnique(modelLayout.status, capacity, 32768);
        if (bars == 0 || voltage == 0 || status == 0)
            return false;
        int charger = r.thumbCall(status + 108);
        if ((r.thumbCall(status + 142) & ~1) != bars || !r.matches(charger & ~1, CHARGER))
            return false;
        int delta = r.thumbCall(resume + modelLayout.deltaCall) & ~1;
        int energy = r.thumbCall(resume + modelLayout.energyCall) & ~1;
        if (!r.matches(delta, DELTA) || !r.matches(energy, modelLayout.energy))
            return false;
        int constants = r.literal(energy + 14);
        if (constants != r.literal(energy + 52) || constants != r.literal(energy + 62)
                || !r.rom(constants, 40) || r.word(constants) != 0 || r.word(constants + 4) != 0
                || r.word(constants + 24) != 0 || r.word(constants + 28) != 0x408f4000
                || r.word(constants + 32) != 0 || r.word(constants + 36) != 0x40ac2000)
            return false;
        int complete = r.findUnique(COMPLETE, delta - 2048, 4096);
        if (complete == 0)
            return false;

        int estimate = r.literal(capacity), monitor = r.literal(resume + 2) - 28;
        int charge = r.literal(delta), model = r.literal(energy + 12);
        for (int i = 0; i < modelLayout.estimateLoads.length; i++)
            if (estimate != r.literal(resume + modelLayout.estimateLoads[i]))
                return false;
        if (monitor != r.literal(bars) + modelLayout.barsBaseBias
                || monitor + 28 != r.literal(voltage) + modelLayout.voltageBaseBias
                || charge != r.literal(complete) || r.literal(nominal & ~1) != model + 40)
            return false;
        if (!r.ram(estimate, 4) || !r.ram(monitor, 64) || !r.ram(charge, 32)
                || !r.ram(model - 64, 112) || !r.ram(r.literal(charger & ~1), 4) || r.error != 0)
            return false;

        int[] api = r.api;
        api[FirmwareApi.BATTERY_CAPACITY] = capacity | 1;
        api[FirmwareApi.BATTERY_NOMINAL] = nominal;
        api[FirmwareApi.BATTERY_BARS] = bars | 1;
        api[FirmwareApi.BATTERY_VOLTAGE] = voltage | 1;
        api[FirmwareApi.BATTERY_CHARGER] = charger;
        api[FirmwareApi.BATTERY_ESTIMATE] = estimate;
        api[FirmwareApi.BATTERY_CHARGE] = charge + 12;
        api[FirmwareApi.BATTERY_ACCOUNTED] = charge + 20;
        api[FirmwareApi.BATTERY_COMPLETE] = charge + 28;
        api[FirmwareApi.BATTERY_PAUSED] = monitor + 15;
        api[FirmwareApi.BATTERY_USED_ENERGY] = model - 64;
        api[FirmwareApi.BATTERY_EMPTY_ENERGY] = model - 64 + 24;
        api[FirmwareApi.BATTERY_MODEL_INVALID] = model + 28;
        api[FirmwareApi.BATTERY_ABI] = FirmwareApi.BATTERY_ABI_RM443;
        return true;
    }
}
