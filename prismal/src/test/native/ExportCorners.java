package com.hellovoid.prismal;
import java.nio.file.*;
import java.util.*;
/** Shader and CPU fixtures consumed by the optional GLES regression test. */
public class ExportCorners {
  public static void main(String[] args) throws Exception {
    Path dir = Path.of(args[0]);
    String shader = PrismalOs4InvariantShader.apply(
        PrismalComponentGateShader.apply(PrismalOpticalEdgeShader.apply(
            PrismalSingleEdgeShader.apply(PrismalShaderSources.FRAGMENT))));
    Files.writeString(dir.resolve("baseline.frag"), shader);
    String decorated = PrismalEdgeDecorationShader.apply(shader);
    Files.writeString(dir.resolve("decorated.frag"), decorated);
    Files.writeString(dir.resolve("reconstructed.frag"),
                      PrismalReconstructed27Shader.apply(decorated));
    String head = shader.substring(0, shader.indexOf("void main() {"));
    String distanceMain = """
   uniform vec2 u_testPoint;
   void main() {
    float r = min(min(u_cornerRadii.x, u_cornerRadii.y), min(u_cornerRadii.z, u_cornerRadii.w));
    r = min(r, min(u_glassSize.x, u_glassSize.y) * 0.5);
    float d = sdRoundBox(u_testPoint, u_glassSize * 0.5, r, u_sminSmoothing);
    float value = clamp(floor((d + 256.0) * 64.0 + 0.5), 0.0, 65535.0);
    gl_FragColor = vec4(floor(value / 256.0), mod(value, 256.0), 0.0, 255.0) / 255.0;
   }
   """;
    Files.writeString(dir.resolve("distance.frag"), head + distanceMain);
    String original = PrismalShaderSources.FRAGMENT;
    Files.writeString(dir.resolve("reference.frag"),
                      original.substring(0, original.indexOf("void main() {")) +
                          distanceMain);
    String depth = shader.substring(0, shader.indexOf("    float tShell ="));
    depth = depth.replace("vec2 pPx = v_shapeCoord * u_glassSize;",
                          "vec2 pPx = u_testPoint;");
    depth = depth.replace("void main() {",
                          "uniform vec2 u_testPoint;\nvoid main() {");
    Files.writeString(dir.resolve("depth.frag"), depth + """
    float value = floor((tDeep * 100.0 + 256.0) * 64.0 + 0.5);
    gl_FragColor = vec4(floor(value / 256.0), mod(value, 256.0), 0.0, 255.0) / 255.0;
   }
   """);
    StringBuilder cases = new StringBuilder();
    for (float[] s : new float[][] {{400, 80, 40},
                                    {100, 100, 50},
                                    {100.01f, 100, 50},
                                    {120, 100, 50},
                                    {200, 160, 24},
                                    {400, 80, 0}}) {
      float[] pts = PrismalContinuousCorners.contour(s[0], s[1], s[2]);
      StringJoiner json = new StringJoiner(",", "[", "]");
      for (int i = 0; i < pts.length; i += 2) {
        cases.append(String.format(Locale.ROOT, "%f %f %f %f %f%n", s[0], s[1],
                                   s[2], pts[i] - s[0] / 2,
                                   s[1] / 2 - pts[i + 1]));
        json.add("[" + pts[i] + "," + pts[i + 1] + "]");
      }
      Files.writeString(dir.resolve("contour-" + (int)s[0] + "-" + (int)s[1] +
                                    "-" + (int)s[2] + ".json"),
                        json.toString());
    }
    Files.writeString(dir.resolve("cases.txt"), cases);
  }
}
