package com.cinecraft.mixin;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Checks hooks against the actual target JAR without initializing Minecraft or a window. */
final class MixinTargetTest {
    @TestFactory
    Stream<DynamicTest> configuredMixinsMatchMinecraftSignatures() throws IOException {
        try (var reader = new InputStreamReader(resource("stillwander.mixins.json"), StandardCharsets.UTF_8)) {
            var config = JsonParser.parseReader(reader).getAsJsonObject();
            String packageName = config.get("package").getAsString();
            return config.getAsJsonArray("client").asList().stream()
                    .map(name -> packageName + "." + name.getAsString())
                    .map(name -> DynamicTest.dynamicTest(name, () -> verifyMixin(name)));
        }
    }

    private static void verifyMixin(String name) throws IOException {
        ClassNode mixin = readClass(name.replace('.', '/'));
        AnnotationNode declaration = annotation(mixin.invisibleAnnotations, "Mixin");
        assertNotNull(declaration, "Missing @Mixin on " + name);
        boolean namedTargets = declaration.values.contains("targets");
        var targets = (List<?>) value(declaration, namedTargets ? "targets" : "value");
        assertEquals(1, targets.size(), "Each port hook has one Minecraft target");
        ClassNode target = readClass(namedTargets ? targets.getFirst().toString().replace('.', '/')
                : ((Type) targets.getFirst()).getInternalName());

        for (var field : mixin.fields) {
            if (annotation(field.visibleAnnotations, "Shadow") == null) continue;
            assertTrue(target.fields.stream().anyMatch(candidate ->
                            candidate.name.equals(field.name) && candidate.desc.equals(field.desc)),
                    "Missing shadow field: " + target.name + "." + field.name + field.desc);
        }
        int injections = 0;
        for (MethodNode handler : mixin.methods) {
            if (annotation(handler.visibleAnnotations, "Shadow") != null) {
                assertTrue(target.methods.stream().anyMatch(candidate ->
                                candidate.name.equals(handler.name) && candidate.desc.equals(handler.desc)),
                        "Missing shadow method: " + target.name + "." + handler.name + handler.desc);
            }
            AnnotationNode inject = annotation(handler.visibleAnnotations, "injection/Inject");
            if (inject == null) continue;
            injections++;
            for (Object selector : (List<?>) value(inject, "method")) {
                List<MethodNode> matches = target.methods.stream()
                        .filter(method -> method.name.equals(selector)).toList();
                assertEquals(1, matches.size(), "Missing or ambiguous injection: " + target.name + "." + selector);
                Type method = Type.getMethodType(matches.getFirst().desc);
                Type[] parameters = Type.getArgumentTypes(handler.desc);
                assertEquals(method.getArgumentTypes().length + 1, parameters.length, handler.name);
                assertArrayEquals(method.getArgumentTypes(), Arrays.copyOf(parameters, parameters.length - 1), handler.name);
                String callback = method.getReturnType().equals(Type.VOID_TYPE)
                        ? "org/spongepowered/asm/mixin/injection/callback/CallbackInfo"
                        : "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";
                assertEquals(callback, parameters[parameters.length - 1].getInternalName(), handler.name);
                if (method.getReturnType().equals(Type.DOUBLE_TYPE)) {
                    assertNotNull(handler.signature, handler.name);
                    assertTrue(handler.signature.contains("<Ljava/lang/Double;>"),
                            "Minecraft 1.21.1 FOV returns double, not float");
                }
            }
        }
        assertTrue(injections > 0, "No injection annotations inspected in " + name);
    }

    private static AnnotationNode annotation(List<AnnotationNode> annotations, String name) {
        if (annotations == null) return null;
        return annotations.stream()
                .filter(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/" + name + ";"))
                .findFirst().orElse(null);
    }

    private static Object value(AnnotationNode annotation, String key) {
        for (int index = 0; index < annotation.values.size(); index += 2) {
            if (annotation.values.get(index).equals(key)) return annotation.values.get(index + 1);
        }
        throw new AssertionError("Missing annotation value: " + key);
    }

    private static ClassNode readClass(String name) throws IOException {
        try (InputStream stream = resource(name + ".class")) {
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static InputStream resource(String name) {
        InputStream stream = MixinTargetTest.class.getClassLoader().getResourceAsStream(name);
        assertNotNull(stream, "Missing classpath resource: " + name);
        return stream;
    }
}
