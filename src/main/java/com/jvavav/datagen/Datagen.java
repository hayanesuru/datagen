package com.jvavav.datagen;

import it.unimi.dsi.fastutil.floats.Float2IntOpenHashMap;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.fabricmc.api.ModInitializer;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.Block;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Util;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EmptyBlockView;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

public class Datagen implements ModInitializer {
    @Override
    public void onInitialize() {
        CrashReport.initCrashReport();
        Bootstrap.initialize();
        Bootstrap.logMissing();
        Util.startTimerHack();

        var b = new StringBuilder(0x10000);
        b.append(SharedConstants.getGameVersion().getName());
        b.append('\n');
        b.append(Integer.toHexString(SharedConstants.getGameVersion().getProtocolVersion()));
        b.append('\n');
        for (var registry : Registries.REGISTRIES) {
            write_head(b, registry.getKey().getValue().getPath(), registry.size());
            write_registry(b, registry);
        }
        gen(b);
        try {
            Files.writeString(Path.of("data.txt"), b.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> void write_registry(StringBuilder b, Registry<T> registry) {
        for (T t : registry) {
            b.append(Objects.requireNonNull(registry.getId(t)).getPath());
            b.append('\n');
        }
    }

    private static void gen(StringBuilder b) {
        var keys = new Object2IntOpenHashMap<String>();
        var vals = new Object2IntOpenHashMap<String>();
        var kvs = new Object2IntOpenHashMap<IntArrayList>();
        var ps = new Object2IntOpenHashMap<IntArrayList>();
        var pps = new Int2IntOpenHashMap();
        write_head(b, "fluid_state",  Fluid.STATE_IDS.size());
        for (FluidState t : Fluid.STATE_IDS) {
            b.append(Registries.FLUID.getId(t.getFluid()).getPath());
            if (!t.isEmpty()) {
                if (t.isStill()) {
                    b.append("_s");
                }
                if (t.get(FlowableFluid.FALLING)) {
                    b.append("_f");
                }
                b.append('_');
                b.append(ih(t.getLevel()));
            }
            b.append('\n');
        }
        for (var block : Registries.BLOCK) {
            var p = block.getStateManager().getProperties();
            if (p.isEmpty()) {
                pps.put(Registries.BLOCK.getRawId(block), -1);
                continue;
            }

            var list2 = new IntArrayList(p.size());
            for (var x : p) {
                keys.putIfAbsent(x.getName(), keys.size());
                var list = new IntArrayList(x.getValues().size() + 1);
                list.add(keys.getInt(x.getName()));
                for (var y : x.getValues()) {
                    var val = Util.getValueAsString(x, y);
                    vals.putIfAbsent(val, vals.size());
                    list.add(vals.getInt(val));
                }
                kvs.putIfAbsent(list, kvs.size());
                list2.add(kvs.getInt(list));
            }
            if (ps.containsKey(list2)) {
                pps.put(Registries.BLOCK.getRawId(block), ps.getInt(list2));
            } else {
                pps.put(Registries.BLOCK.getRawId(block), ps.size());
                ps.putIfAbsent(list2, ps.size());
            }
        }

        var keyz = new ObjectArrayList<String>(keys.size());
        keyz.size(keys.size());
        for (var key : keys.object2IntEntrySet()) {
            keyz.set(key.getIntValue(), key.getKey());
        }
        write_head(b, "block_state_property_key", keyz.size());
        for (var name : keyz) {
            b.append(name);
            b.append('\n');
        }

        var valz = new ObjectArrayList<String>(vals.size());
        valz.size(vals.size());
        for (var val : vals.object2IntEntrySet()) {
            valz.set(val.getIntValue(), val.getKey());
        }
        write_head(b, "block_state_property_value", valz.size());
        for (var name : valz) {
            b.append(name);
            b.append('\n');
        }

        var kvz = new ObjectArrayList<IntArrayList>(kvs.size());
        kvz.size(kvs.size());
        for (var key : kvs.object2IntEntrySet()) {
            kvz.set(key.getIntValue(), key.getKey());
        }

        var pz = new ObjectArrayList<IntArrayList>(ps.size());
        pz.size(ps.size());
        for (var key : ps.object2IntEntrySet()) {
            pz.set(key.getIntValue(), key.getKey());
        }

        write_head(b, "block_state_property", kvz.size());
        for (var x : kvz) {
            boolean first = true;
            for (var y : x) {
                if (!first) {
                    b.append(' ');
                }
                first = false;
                b.append(Integer.toHexString(y));
            }
            b.append('\n');
        }
        write_head(b, "block_state_properties", pz.size());
        for (var x : pz) {
            boolean first = true;
            for (var x1 : x) {
                if (!first) {
                    b.append(' ');
                }
                first = false;
                b.append(ih(x1));
            }
            b.append('\n');
        }

        write_head(b, "block_state", Registries.BLOCK.size());
        write_rl(b, Registries.BLOCK.stream().mapToInt(it -> pps.get(Registries.BLOCK.getRawId(it))));

        write_head(b, "block_to_block_state", Registries.BLOCK.size());
        for (var block : Registries.BLOCK) {
            b.append(ih(Block.STATE_IDS.getRawId(block.getDefaultState())));
            b.append('\n');
        }
        write_head(b, "item_to_block", Registries.ITEM.size());
        var item_to_block = new int[Registries.ITEM.size()];
        for (var x : Registries.ITEM) {
            if (x instanceof BlockItem item) {
                item_to_block[Registries.ITEM.getRawId(item.asItem())] = Registries.BLOCK.getRawId(item.getBlock());
            }
        }
        for (var block : item_to_block) {
            b.append(ih(block));
            b.append('\n');
        }

        var f32s = new Float2IntOpenHashMap(128);
        f32s.put(0.0F, 0);
        for (var block : Registries.BLOCK) {
            f32s.putIfAbsent(block.getDefaultState().getHardness(EmptyBlockView.INSTANCE, BlockPos.ORIGIN), f32s.size());
            f32s.putIfAbsent(block.getSlipperiness(), f32s.size());
            f32s.putIfAbsent(block.getBlastResistance(), f32s.size());
            f32s.putIfAbsent(block.getVelocityMultiplier(), f32s.size());
            f32s.putIfAbsent(block.getJumpVelocityMultiplier(), f32s.size());
        }

        var f32z = new FloatArrayList(f32s.size());
        f32z.size(f32s.size());
        for (var e : f32s.float2IntEntrySet()) {
            var k = e.getFloatKey();
            var v = e.getIntValue();
            f32z.set(v, k);
        }

        write_head(b, "float32_table", f32z.size());
        for (var e : f32z) {
            b.append(ih(Float.floatToIntBits(e)));
            b.append('\n');
        }

        write_head(b, "block_settings_hardness", Registries.BLOCK.size());
        write_rl(b, Registries.BLOCK.stream().mapToInt(x -> f32s.get(x.getDefaultState().getHardness(EmptyBlockView.INSTANCE, BlockPos.ORIGIN))));

        write_head(b, "block_settings_slipperiness", Registries.BLOCK.size());
        write_rl(b, Registries.BLOCK.stream().mapToInt(x -> f32s.get(x.getSlipperiness())));

        write_head(b, "block_settings_blast_resistance", Registries.BLOCK.size());
        write_rl(b, Registries.BLOCK.stream().mapToInt(x -> f32s.get(x.getBlastResistance())));

        write_head(b, "block_settings_velocity_multiplier", Registries.BLOCK.size());
        write_rl(b, Registries.BLOCK.stream().mapToInt(x -> f32s.get(x.getVelocityMultiplier())));

        write_head(b, "block_settings_jump_velocity_multiplier", Registries.BLOCK.size());
        write_rl(b, Registries.BLOCK.stream().mapToInt(x -> f32s.get(x.getJumpVelocityMultiplier())));

        write_head(b, "item_max_count", Registries.ITEM.size());
        write_rl(b, Registries.ITEM.stream().mapToInt(Item::getMaxCount));

        write_head(b, "fluid_to_block", Fluid.STATE_IDS.size());
        for (var f : Fluid.STATE_IDS) {
            b.append(ih(Block.STATE_IDS.getRawId(f.getBlockState())));
            b.append('\n');
        }
        write_head(b, "fluid_state_level", Fluid.STATE_IDS.size());
        for (var f : Fluid.STATE_IDS) {
            b.append(ih(f.getLevel()));
            b.append('\n');
        }
        write_head(b, "fluid_state_falling", Fluid.STATE_IDS.size());
        for (var f : Fluid.STATE_IDS) {
            b.append(f.isEmpty() ? '0' : f.get(FlowableFluid.FALLING) ? '1' : '0');
            b.append('\n');
        }
        write_head(b, "fluid_state_to_fluid", Fluid.STATE_IDS.size());
        for (var f : Fluid.STATE_IDS) {
            b.append(ih(Registries.FLUID.getRawId(f.getFluid())));
            b.append('\n');
        }

        write_head(b, "block_state_to_fluid_state", Block.STATE_IDS.size());
        write_rl(b, StreamSupport.stream(Block.STATE_IDS.spliterator(), false).mapToInt(it -> Fluid.STATE_IDS.getRawId(it.getFluidState())));
    }

    public static void write_head(StringBuilder b, String name, int size) {
        b.append(';');
        b.append(name);
        b.append(';');
        b.append(ih(size));
        b.append('\n');
    }

    public static void write_rl(StringBuilder b, IntStream stream) {
        var i = stream.iterator();
        if (!i.hasNext()) {
            return;
        }
        int ncount = 1;
        int nval = i.nextInt();
        while (i.hasNext()) {
            int val = i.nextInt();
            if (val == nval) {
                ncount += 1;
            } else if (ncount == 1) {
                b.append(ih(nval));
                b.append('\n');
                nval = val;
            } else {
                b.append('~');
                b.append(ih(ncount));
                b.append(' ');
                b.append(ih(nval));
                b.append('\n');
                ncount = 1;
                nval = val;
            }
        }
        if (ncount == 1) {
            b.append(ih(nval));
            b.append('\n');
        } else if (ncount != 0) {
            b.append('~');
            b.append(ih(ncount));
            b.append(' ');
            b.append(ih(nval));
            b.append('\n');
        }
    }

    public static String ih(int x) {
        return Integer.toHexString(x);
    }
}
