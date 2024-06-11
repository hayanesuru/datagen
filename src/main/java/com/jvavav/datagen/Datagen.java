package com.jvavav.datagen;

import it.unimi.dsi.fastutil.doubles.Double2IntOpenHashMap;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.floats.Float2IntOpenHashMap;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.fabricmc.api.ModInitializer;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.Block;
import net.minecraft.block.SideShapeType;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.BlockItem;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.NetworkState;
import net.minecraft.util.Util;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.EmptyBlockView;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

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
        packetId(b);
        for (var registry : Registry.REGISTRIES) {
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


    private static void packetId(StringBuilder b) {
        var list = new ObjectArrayList<String>(128);
        for (NetworkState state : NetworkState.values()) {
            for (var entry : state.packetHandlers.entrySet()) {
                NetworkSide side = entry.getKey();
                NetworkState.PacketHandler<?> x = entry.getValue();
                list.size(x.packetIds.size());
                for (var e : x.packetIds.object2IntEntrySet()) {
                    var id = e.getIntValue();
                    var clazz = e.getKey();
                    list.set(id, getPacketName(clazz));
                }
                var n = switch (side) {
                    case CLIENTBOUND -> state.name().toLowerCase(Locale.ENGLISH) + "_s2c";
                    case SERVERBOUND -> state.name().toLowerCase(Locale.ENGLISH) + "_c2s";
                };
                write_head(b, n, list.size());
                for (String s : list) {
                    b.append(s);
                    b.append("\n");
                }
            }
        }
    }

    private static @NotNull String getPacketName(Class<?> x) {
        var clazz = x.getSimpleName();
        if (clazz.endsWith("C2SPacket") || clazz.endsWith("S2CPacket")) {
            clazz = clazz.substring(0, clazz.length() - 9);
        } else {
            var superClazz = x.getSuperclass().getSimpleName();
            if (superClazz.endsWith("C2SPacket") || superClazz.endsWith("S2CPacket")) {
                clazz = superClazz.substring(0, superClazz.length() - 9) + clazz;
            } else {
                clazz = superClazz;
            }
        }
        return clazz;
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
        write_head(b, "fluid_state",  Fluid.STATE_IDS.size());
        for (FluidState t : Fluid.STATE_IDS) {
            b.append(Registry.FLUID.getId(t.getFluid()).getPath());
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
        for (var block : Registry.BLOCK) {
            var p = block.getStateManager().getProperties();
            if (p.isEmpty()) {
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
            ps.putIfAbsent(list2, ps.size());
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

        write_head(b, "block_state", Registry.BLOCK.size());
        var ncount = 0;
        var nval = 0;
        for (var block : Registry.BLOCK) {
            int val = -1;
            if (!block.getStateManager().getProperties().isEmpty()) {
                var list = new IntArrayList(block.getStateManager().getProperties().size());
                for (var prop : block.getStateManager().getProperties()) {
                    var list2 = new IntArrayList(prop.getValues().size() + 1);
                    list2.add(keys.getInt(prop.getName()));
                    for (var x : prop.getValues()) {
                        list2.add(vals.getInt(Util.getValueAsString(prop, x)));
                    }
                    list.add(kvs.getInt(list2));
                }
                val = ps.getInt(list);
            }
            if (ncount == 0) {
                ncount = 1;
                nval = val;
            } else if (val == nval) {
                ncount += 1;
            } else if (ncount == 1) {
                if (nval != -1) {
                    b.append(ih(nval));
                }
                b.append('\n');
                nval = val;
            } else {
                b.append('~');
                b.append(ih(ncount));
                if (nval != -1) {
                    b.append(' ');
                    b.append(ih(nval));
                }
                b.append('\n');
                ncount = 1;
                nval = val;
            }
        }
        if (ncount == 1) {
            if (nval != -1) {
                b.append(ih(nval));
            }
            b.append('\n');
        } else if (ncount != 0) {
            b.append('~');
            b.append(ih(ncount));
            if (nval != -1) {
                b.append(' ');
                b.append(ih(nval));
            }
            b.append('\n');
        }
        ncount = 0;

        write_head(b, "block_to_block_state", Registry.BLOCK.size());
        for (var block : Registry.BLOCK) {
            b.append(ih(Block.STATE_IDS.getRawId(block.getDefaultState())));
            b.append('\n');
        }
        write_head(b, "item_to_block", Registry.ITEM.size());
        var item_to_block = new int[Registry.ITEM.size()];
        for (var x : Registry.ITEM) {
            if (x instanceof BlockItem item) {
                item_to_block[Registry.ITEM.getRawId(item.asItem())] = Registry.BLOCK.getRawId(item.getBlock());
            }
        }
        for (var block : item_to_block) {
            b.append(ih(block));
            b.append('\n');
        }

        var f32s = new Float2IntOpenHashMap(128);
        f32s.put(0.0f, 0);
        for (var block : Registry.BLOCK) {
            f32s.putIfAbsent(block.getDefaultState().getHardness(EmptyBlockView.INSTANCE, BlockPos.ORIGIN), f32s.size());
            f32s.putIfAbsent(block.getSlipperiness(), f32s.size());
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

        var shapes = new Object2IntOpenHashMap<List<Box>>(128);
        for (var block : Registry.BLOCK) {
            if (block.hasDynamicBounds()) {
                continue;
            }
            for (var state : block.getStateManager().getStates()) {
                shapes.putIfAbsent(state.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN).getBoundingBoxes(), shapes.size());
                if (state.isOpaque()) {
                    shapes.putIfAbsent(state.getCullingShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN).getBoundingBoxes(), shapes.size());
                }
            }
        }

        var shapes2 = new ObjectArrayList<List<Box>>(shapes.size());
        shapes2.size(shapes.size());
        for (var e : shapes.object2IntEntrySet()) {
            var k = e.getKey();
            var v = e.getIntValue();
            shapes2.set(v, k);
        }

        var f64s = new Double2IntOpenHashMap(128);
        f64s.put(0.0, 0);
        for (var shape : shapes2) {
            for (var box : shape) {
                f64s.putIfAbsent(box.minX, f64s.size());
                f64s.putIfAbsent(box.minY, f64s.size());
                f64s.putIfAbsent(box.minZ, f64s.size());
                f64s.putIfAbsent(box.maxX, f64s.size());
                f64s.putIfAbsent(box.maxY, f64s.size());
                f64s.putIfAbsent(box.maxZ, f64s.size());
            }
        }
        var f64z = new DoubleArrayList(f64s.size());
        f64z.size(f64s.size());
        for (var e : f64s.double2IntEntrySet()) {
            var k = e.getDoubleKey();
            var v = e.getIntValue();
            f64z.set(v, k);
        }
        write_head(b, "float64_table", f64z.size());
        for (var f64 : f64z) {
            b.append(Long.toHexString(Double.doubleToLongBits(f64)));
            b.append('\n');
        }
        write_head(b, "shape_table", shapes.size());
        for (var e : shapes2) {
            boolean first = true;
            for (var x : e) {
                if (!first) {
                    b.append(' ');
                }
                first = false;

                b.append(ih(f64s.get(x.minX)));
                b.append(' ');
                b.append(ih(f64s.get(x.minY)));
                b.append(' ');
                b.append(ih(f64s.get(x.minZ)));
                b.append(' ');
                b.append(ih(f64s.get(x.maxX)));
                b.append(' ');
                b.append(ih(f64s.get(x.maxY)));
                b.append(' ');
                b.append(ih(f64s.get(x.maxZ)));
            }
            b.append('\n');
        }

        write_head(b, "block_settings#hardness " +
                "blastresistance slipperiness velocity_multiplier " +
                "jump_velocity_multiplier", Registry.BLOCK.size());

        for (var block : Registry.BLOCK) {
            float xf32a = block.getDefaultState().getHardness(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
            float xf32b = block.getBlastResistance();
            float xf32c = block.getSlipperiness();
            float xf32d = block.getVelocityMultiplier();
            float xf32e = block.getJumpVelocityMultiplier();
            b.append(ih(f32s.get(xf32a)));
            b.append(' ');
            b.append(ih(f32s.get(xf32b)));
            b.append(' ');
            b.append(ih(f32s.get(xf32c)));
            b.append(' ');
            b.append(ih(f32s.get(xf32d)));
            b.append(' ');
            b.append(ih(f32s.get(xf32e)));
            b.append('\n');
        }

        var lastb = Registry.BLOCK.get(Registry.BLOCK.size() - 1);
        var lastid = Block.STATE_IDS.getRawId(lastb.getStateManager().getStates().get(lastb.getStateManager().getStates().size() - 1));
        int mval = 0;

        write_head(b, "block_state_settings#" +
                "luminance (has_sided_transparency lava_ignitable " +
                "material_replaceable opaque tool_required " +
                "exceeds_cube redstone_power_source " +
                "has_comparator_output)", lastid + 1);
        for (var block : Registry.BLOCK) {
            for (var state : block.getStateManager().getStates()) {
                int flags = (state.hasComparatorOutput() ? 0b1 : 0) |
                        (state.emitsRedstonePower() ? 0b10 : 0) |
                        (state.exceedsCube() ? 0b100 : 0) |
                        (state.isToolRequired() ? 0b1000 : 0) |
                        (state.isOpaque() ? 0b10000 : 0) |
                        (state.getMaterial().isReplaceable() ? 0b100000 : 0) |
                        (state.getMaterial().isBurnable() ? 0b1000000 : 0) |
                        (state.hasSidedTransparency() ? 0b10000000 : 0);
                int lumi = state.getLuminance();

                if (ncount == 0) {
                    ncount = 1;
                    nval = flags;
                    mval = lumi;
                } else if (flags == nval && lumi == mval) {
                    ncount += 1;
                } else if (ncount == 1) {
                    b.append(ih(mval));
                    b.append(' ');
                    b.append(ih(nval));
                    b.append('\n');
                    nval = flags;
                    mval = lumi;
                } else {
                    b.append('~');
                    b.append(ih(ncount));
                    b.append(' ');
                    b.append(ih(mval));
                    b.append(' ');
                    b.append(ih(nval));
                    b.append('\n');
                    ncount = 1;
                    nval = flags;
                    mval = lumi;
                }
            }
        }
        if (ncount == 1) {
            b.append(ih(mval));
            b.append(' ');
            b.append(ih(nval));
            b.append('\n');
        } else if (ncount != 0) {
            b.append('~');
            b.append(ih(ncount));
            b.append(' ');
            b.append(ih(mval));
            b.append(' ');
            b.append(ih(nval));
            b.append('\n');
        }
        ncount = 0;

        var bounds = new Object2IntOpenHashMap<IntArrayList>();
        var boundx = new IntArrayList(lastid + 1);
        for (var block : Registry.BLOCK) {
            boolean isdyn = block.hasDynamicBounds();
            if (isdyn) {
                int size = block.getStateManager().getStates().size();
                for (int i = 0; i < size; i++) {
                    boundx.push(Integer.MAX_VALUE);
                }
                continue;
            }
            for (var state : block.getStateManager().getStates()) {
                int flags1 = 0;
                if (state.isOpaqueFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN)) {
                    flags1 |= 1;
                }
                if (state.isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN)) {
                    flags1 |= 2;
                }
                if (state.isTranslucent(EmptyBlockView.INSTANCE, BlockPos.ORIGIN)) {
                    flags1 |= 4;
                }
                if (state.isSolidBlock(EmptyBlockView.INSTANCE, BlockPos.ORIGIN)) {
                    flags1 |= 8;
                }
                flags1 |= state.getOpacity(EmptyBlockView.INSTANCE, BlockPos.ORIGIN) << 4;

                int flags2 = 0;
                int flagPos = 0;
                for (var direction : Direction.values()) {
                    boolean flag = state.isSideSolid(EmptyBlockView.INSTANCE, BlockPos.ORIGIN, direction, SideShapeType.FULL);
                    if (flag) {
                        flags2 |= 1 << flagPos;
                    }
                    flagPos++;
                }

                int flags3 = 0;
                flagPos = 0;
                for (var direction : Direction.values()) {
                    boolean flag = state.isSideSolid(EmptyBlockView.INSTANCE, BlockPos.ORIGIN, direction, SideShapeType.CENTER);
                    if (flag) {
                        flags3 |= 1 << flagPos;
                    }
                    flagPos++;
                }

                int flags4 = 0;
                flagPos = 0;

                for (var direction : Direction.values()) {
                    boolean flag = state.isSideSolid(EmptyBlockView.INSTANCE, BlockPos.ORIGIN, direction, SideShapeType.RIGID);
                    if (flag) {
                        flags4 |= 1 << flagPos;
                    }
                    flagPos++;
                }

                int flags5 = shapes.getInt(state.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN).getBoundingBoxes());
                int flags6 = shapes.getInt(state.getCullingShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN).getBoundingBoxes());
                var x = new IntArrayList(new int[]{flags1, flags2, flags3, flags4, flags5, flags6});
                bounds.putIfAbsent(x, bounds.size());
                boundx.push(bounds.getInt(x));
            }
        }

        var boundz = new ObjectArrayList<IntArrayList>(bounds.size());
        boundz.size(bounds.size());
        for (var e : bounds.object2IntEntrySet()) {
            var k = e.getKey();
            var v = e.getIntValue();
            boundz.set(v, k);
        }
        write_head(b, "block_state_static_bounds_table#" +
                "(opacity(4) solid_block translucent full_cube " +
                "opaque_full_cube) side_solid_full " +
                "side_solid_center side_solid_rigid " +
                "collision_shape culling_shape", boundz.size());
        for (var bound : boundz) {
            boolean first = true;
            for (var x : bound) {
                if (!first) {
                    b.append(' ');
                }
                first = false;
                b.append(ih(x));
            }
            b.append('\n');
        }

        write_head(b, "block_state_static_bounds", lastid + 1);
        for (var val : boundx) {
            if (val == Integer.MAX_VALUE) {
                val = -1;
            }

            if (ncount == 0) {
                ncount = 1;
                nval = val;
            } else if (val == nval) {
                ncount += 1;
            } else if (ncount == 1) {
                if (nval != -1) {
                    b.append(ih(nval));
                }
                b.append('\n');
                nval = val;
            } else {
                b.append('~');
                b.append(ih(ncount));
                if (nval != -1) {
                    b.append(' ');
                    b.append(ih(nval));
                }
                b.append('\n');
                ncount = 1;
                nval = val;
            }
        }
        if (ncount == 1) {
            if (nval != -1) {
                b.append(ih(nval));
            }
            b.append('\n');
        } else if (ncount != 0) {
            b.append('~');
            b.append(ih(ncount));
            if (nval != -1) {
                b.append(' ');
                b.append(ih(nval));
            }
            b.append('\n');
        }
        ncount = 0;

        write_head(b, "item_max_count", Registry.ITEM.size());
        for (var item : Registry.ITEM) {
            int val = item.getMaxCount();
            if (ncount == 0) {
                ncount = 1;
                nval = val;
            } else if (val == nval) {
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
        ncount = 0;
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
            b.append(ih(Registry.FLUID.getRawId(f.getFluid())));
            b.append('\n');
        }
        write_head(b, "block_to_fluid_state", Block.STATE_IDS.size());
        for (var state : Block.STATE_IDS) {
            var val = Fluid.STATE_IDS.getRawId(state.getFluidState());
            if (ncount == 0) {
                ncount = 1;
                nval = val;
            } else if (val == nval) {
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

    public static void write_head(StringBuilder b, String name, int size) {
        b.append(';');
        b.append(name);
        b.append(';');
        b.append(ih(size));
        b.append('\n');
    }

    public static String ih(int x) {
        return Integer.toHexString(x);
    }
}
