import React, { useState } from "react";
import {
  SafeAreaView,
  View,
  Text,
  Pressable,
  StyleSheet,
  ScrollView,
} from "react-native";

const INDIGO = "#7472F4";

const faces = [
  { id: "heart", glyph: "♥️", label: "Love", color: "#EF4A63" },
  { id: "laughing", glyph: "😆", label: "Laugh", color: "#FFCC4D" },
  { id: "wow", glyph: "😮", label: "Wow", color: "#FFD39A" },
  { id: "sad", glyph: "😢", label: "Sad", color: "#9CC8FF" },
  { id: "thumbs-up", glyph: "👍", label: "Like", color: "#FFD39A" },
];

const tabs = ["Home", "Identity", "Chat", "Market"];

export default function App() {
  const [tab, setTab] = useState("Home");
  const [selected, setSelected] = useState(faces[0]);

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.app}>
        <ScrollView contentContainerStyle={styles.content}>
          <View style={styles.header}>
            <View>
              <Text style={styles.eyebrow}>ITUNDA</Text>
              <Text style={styles.title}>{tab}</Text>
            </View>
            <View style={styles.avatar}>
              <Text style={styles.avatarText}>E</Text>
            </View>
          </View>

          {tab === "Home" && (
            <>
              <View style={styles.heroCard}>
                <Text style={styles.heroEyebrow}>WELCOME</Text>
                <Text style={styles.heroTitle}>Everything you need,</Text>
                <Text style={styles.heroTitle}>in one place.</Text>
                <Text style={styles.heroBody}>
                  Simple services for everyday life in Rwanda.
                </Text>
              </View>

              <Text style={styles.sectionTitle}>Quick actions</Text>
              <View style={styles.actions}>
                {[
                  ["💳", "Pay"],
                  ["💬", "Chat"],
                  ["🛍️", "Market"],
                  ["🪪", "Identity"],
                ].map(([icon, label]) => (
                  <Pressable key={label} style={styles.action}>
                    <Text style={styles.actionIcon}>{icon}</Text>
                    <Text style={styles.actionLabel}>{label}</Text>
                  </Pressable>
                ))}
              </View>

              <View style={styles.faceCard}>
                <View style={styles.faceHeader}>
                  <View>
                    <Text style={styles.sectionTitle}>ItundaFace</Text>
                    <Text style={styles.muted}>Choose your expression</Text>
                  </View>
                  <Text style={styles.faceBadge}>ORIGINAL</Text>
                </View>

                <View style={styles.selectedFace}>
                  <View
                    style={[
                      styles.faceOrb,
                      { backgroundColor: selected.color },
                    ]}
                  >
                    <Text style={styles.faceGlyph}>{selected.glyph}</Text>
                  </View>
                  <View style={styles.selectedCopy}>
                    <Text style={styles.selectedName}>{selected.label}</Text>
                    <Text style={styles.muted}>
                      {selected.id} · ItundaFace
                    </Text>
                  </View>
                </View>

                <ScrollView
                  horizontal
                  showsHorizontalScrollIndicator={false}
                  contentContainerStyle={styles.faceRow}
                >
                  {faces.map((face) => (
                    <Pressable
                      key={face.id}
                      onPress={() => setSelected(face)}
                      style={[
                        styles.faceChoice,
                        selected.id === face.id && styles.faceChoiceActive,
                      ]}
                    >
                      <Text style={styles.faceChoiceGlyph}>{face.glyph}</Text>
                    </Pressable>
                  ))}
                </ScrollView>
              </View>
            </>
          )}

          {tab !== "Home" && (
            <View style={styles.placeholder}>
              <Text style={styles.placeholderIcon}>
                {tab === "Identity" ? "🪪" : tab === "Chat" ? "💬" : "🛍️"}
              </Text>
              <Text style={styles.placeholderTitle}>{tab}</Text>
              <Text style={styles.muted}>
                This screen is ready for the next Itunda prototype pass.
              </Text>
            </View>
          )}
        </ScrollView>

        <View style={styles.tabBar}>
          {tabs.map((item) => (
            <Pressable
              key={item}
              onPress={() => setTab(item)}
              style={styles.tab}
            >
              <View
                style={[
                  styles.tabDot,
                  tab === item && styles.tabDotActive,
                ]}
              />
              <Text
                style={[
                  styles.tabText,
                  tab === item && styles.tabTextActive,
                ]}
              >
                {item}
              </Text>
            </Pressable>
          ))}
        </View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: "#F7F7FA" },
  app: { flex: 1 },
  content: { padding: 20, paddingBottom: 110 },
  header: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: 24,
  },
  eyebrow: {
    color: INDIGO,
    fontSize: 12,
    fontWeight: "800",
    letterSpacing: 1.5,
  },
  title: { fontSize: 32, fontWeight: "800", marginTop: 3, color: "#111116" },
  avatar: {
    width: 42,
    height: 42,
    borderRadius: 21,
    backgroundColor: "#ECECFF",
    alignItems: "center",
    justifyContent: "center",
  },
  avatarText: { color: INDIGO, fontWeight: "800" },
  heroCard: {
    backgroundColor: INDIGO,
    borderRadius: 28,
    padding: 26,
    marginBottom: 28,
  },
  heroEyebrow: {
    color: "#EDEDFF",
    fontSize: 11,
    fontWeight: "800",
    letterSpacing: 1.4,
    marginBottom: 14,
  },
  heroTitle: { color: "#FFF", fontSize: 28, fontWeight: "800", lineHeight: 32 },
  heroBody: {
    color: "#ECECFF",
    fontSize: 15,
    lineHeight: 22,
    marginTop: 16,
    maxWidth: 300,
  },
  sectionTitle: { fontSize: 19, fontWeight: "800", color: "#111116" },
  muted: { color: "#777781", fontSize: 13, marginTop: 4 },
  actions: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 10,
    marginTop: 14,
    marginBottom: 28,
  },
  action: {
    width: "48%",
    minHeight: 92,
    borderRadius: 20,
    backgroundColor: "#FFF",
    padding: 16,
    justifyContent: "space-between",
  },
  actionIcon: { fontSize: 28 },
  actionLabel: { fontWeight: "700", color: "#222228" },
  faceCard: {
    backgroundColor: "#FFF",
    borderRadius: 26,
    padding: 20,
  },
  faceHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
  },
  faceBadge: {
    fontSize: 9,
    fontWeight: "800",
    color: INDIGO,
    backgroundColor: "#F0F0FF",
    paddingHorizontal: 9,
    paddingVertical: 5,
    borderRadius: 10,
  },
  selectedFace: {
    flexDirection: "row",
    alignItems: "center",
    marginTop: 22,
  },
  faceOrb: {
    width: 92,
    height: 92,
    borderRadius: 46,
    alignItems: "center",
    justifyContent: "center",
  },
  faceGlyph: { fontSize: 54 },
  selectedCopy: { marginLeft: 16 },
  selectedName: { fontSize: 20, fontWeight: "800", color: "#17171C" },
  faceRow: { gap: 10, paddingTop: 18 },
  faceChoice: {
    width: 58,
    height: 58,
    borderRadius: 18,
    backgroundColor: "#F6F6F8",
    alignItems: "center",
    justifyContent: "center",
  },
  faceChoiceActive: { backgroundColor: "#EEEEFF", borderWidth: 2, borderColor: INDIGO },
  faceChoiceGlyph: { fontSize: 31 },
  placeholder: {
    flex: 1,
    minHeight: 500,
    alignItems: "center",
    justifyContent: "center",
  },
  placeholderIcon: { fontSize: 56, marginBottom: 18 },
  placeholderTitle: { fontSize: 28, fontWeight: "800", color: "#111116" },
  tabBar: {
    position: "absolute",
    left: 12,
    right: 12,
    bottom: 12,
    height: 72,
    borderRadius: 24,
    backgroundColor: "rgba(255,255,255,0.97)",
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-around",
    shadowOpacity: 0.08,
    shadowRadius: 18,
    shadowOffset: { width: 0, height: 6 },
    elevation: 8,
  },
  tab: { alignItems: "center", justifyContent: "center", minWidth: 70 },
  tabDot: { width: 5, height: 5, borderRadius: 3, backgroundColor: "#C9C9D0", marginBottom: 6 },
  tabDotActive: { width: 7, height: 7, borderRadius: 4, backgroundColor: INDIGO },
  tabText: { fontSize: 11, color: "#888890", fontWeight: "600" },
  tabTextActive: { color: INDIGO, fontWeight: "800" },
});
