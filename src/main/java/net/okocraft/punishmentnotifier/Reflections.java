package net.okocraft.punishmentnotifier;

import net.kyori.adventure.text.Component;
import space.arim.libertybans.api.LibertyBans;
import space.arim.libertybans.api.formatter.PunishmentFormatter;
import space.arim.libertybans.api.punish.Punishment;
import space.arim.libertybans.api.user.AltAccount;
import space.arim.omnibus.OmnibusProvider;
import space.arim.omnibus.util.concurrent.CentralisedFuture;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;

final class Reflections {

    private static final MethodHandle GET_PUNISHMENT_MESSAGE_METHOD;

    static {
        try {
            GET_PUNISHMENT_MESSAGE_METHOD = initializeMethod();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static MethodHandle initializeMethod() throws Exception {
        var formatter = OmnibusProvider.getOmnibus().getRegistry().getProvider(LibertyBans.class).orElseThrow().getFormatter();
        var methodType = MethodType.methodType(CentralisedFuture.class, Punishment.class);
        return MethodHandles.lookup().findVirtual(formatter.getClass(), "getPunishmentMessage", methodType);
    }

    @SuppressWarnings("unchecked")
    static CentralisedFuture<Component> getPunishmentMessage(PunishmentFormatter formatter, Punishment punishment) throws Throwable {
        return (CentralisedFuture<Component>) GET_PUNISHMENT_MESSAGE_METHOD.invoke(formatter, punishment);
    }

    private static RecordComponent DETECTION_KIND_FIELD;

    static RecordComponent getDetectionKindComponent(Class<?> clazz) {
        return Arrays.stream(clazz.getRecordComponents()).filter(component -> component.getName().equals("detectionKind")).findFirst().orElseThrow();
    }

    static boolean isNormalDetection(AltAccount account) {
        if (DETECTION_KIND_FIELD == null) {
            DETECTION_KIND_FIELD = getDetectionKindComponent(account.getClass());
        }

        try {
            return ((Enum<?>) DETECTION_KIND_FIELD.getAccessor().invoke(account)).name().equals("NORMAL");
        } catch (Exception e) {
            return false;
        }
    }
}
