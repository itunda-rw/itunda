import React, { useState } from "react";
import { SafeAreaView, View, Text, Pressable, StyleSheet, ScrollView, Linking, Alert } from "react-native";

const INDIGO = "#7472F4";
const screens = ["Home", "Identity", "Chat", "Marketplace", "Mini Apps"];

async function openNative(route) {
  try {
    await Linking.openURL(`itunda://test/${route}`);
  } catch (_error) {
    Alert.alert("Itunda app not available", "Install the latest Itunda Android development build, then try again.");
  }
}

function routeFor(label) {
  return {"Home":"home","Identity":"identity","Chat":"messages","Marketplace":"marketplace","Mini Apps":"miniapps"}[label];
}

function Header() {
  return (
    <View style={styles.header}>
      <View>
        <Text style={styles.eyebrow}>ITUNDA</Text>
        <Text style={styles.title}>Snack Lab</Text>
      </View>
      <View style={styles.badge}><Text style={styles.badgeText}>ANDROID</Text></View>
    </View>
  );
}

export default function App() {
  const [screen, setScreen] = useState("Home");

  const launch = (label) => {
    setScreen(label);
    openNative(routeFor(label));
  };

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.app}>
        <ScrollView contentContainerStyle={styles.content}>
          <Header />

          <View style={styles.hero}>
            <Text style={styles.heroEyebrow}>NATIVE TEST LAB</Text>
            <Text style={styles.heroTitle}>Test the real Itunda app.</Text>
            <Text style={styles.heroBody}>
              These controls launch the native Android screens. Snack Lab is only the test launcher.
            </Text>
          </View>

          <Text style={styles.section}>Test screens</Text>
          <View style={styles.list}>
            {screens.map((label, index) => (
              <Pressable
                key={label}
                testID={`test-${label.toLowerCase().replace(/\\s+/g,"-")}`}
                accessibilityRole="button"
                accessibilityLabel={`Launch Itunda ${label}`}
                style={({pressed}) => [styles.testButton, pressed && styles.pressed]}
                onPress={() => launch(label)}
              >
                <View style={styles.number}><Text style={styles.numberText}>{index + 1}</Text></View>
                <View style={styles.buttonCopy}>
                  <Text style={styles.buttonTitle}>{label}</Text>
                  <Text style={styles.buttonSubtitle}>Open real native screen</Text>
                </View>
                <Text style={styles.arrow}>›</Text>
              </Pressable>
            ))}
          </View>

          <View style={styles.info}>
            <Text style={styles.infoTitle}>How it works</Text>
            <Text style={styles.infoText}>Snack Lab → itunda://test/* → Itunda Android</Text>
          </View>
        </ScrollView>

        <View testID="bottom-navigation" style={styles.nav}>
          {screens.map(x => (
            <Pressable
              key={x}
              testID={`nav-${x.toLowerCase().replace(/\\s+/g,"-")}`}
              accessibilityRole="button"
              style={styles.navItem}
              onPress={() => launch(x)}
            >
              <Text style={[styles.navText, screen === x && styles.active]}>{x}</Text>
            </Pressable>
          ))}
        </View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe:{flex:1,backgroundColor:"#F7F7FA"},
  app:{flex:1},
  content:{padding:20,paddingBottom:120},
  header:{flexDirection:"row",justifyContent:"space-between",alignItems:"center",marginBottom:24},
  eyebrow:{fontSize:12,fontWeight:"800",letterSpacing:1.5,color:INDIGO},
  title:{fontSize:32,fontWeight:"800",color:"#111116",marginTop:3},
  badge:{backgroundColor:"#ECECFF",borderRadius:12,paddingHorizontal:10,paddingVertical:7},
  badgeText:{fontSize:10,fontWeight:"800",color:INDIGO,letterSpacing:.7},
  hero:{backgroundColor:INDIGO,borderRadius:28,padding:26,marginBottom:28},
  heroEyebrow:{fontSize:11,fontWeight:"800",letterSpacing:1.3,color:"#ECECFF",marginBottom:14},
  heroTitle:{fontSize:28,lineHeight:33,fontWeight:"800",color:"#FFF"},
  heroBody:{fontSize:15,lineHeight:22,color:"#ECECFF",marginTop:16},
  section:{fontSize:19,fontWeight:"800",color:"#111116",marginBottom:14},
  list:{gap:10},
  testButton:{minHeight:78,borderRadius:20,backgroundColor:"#FFF",padding:14,flexDirection:"row",alignItems:"center"},
  pressed:{opacity:.72},
  number:{width:42,height:42,borderRadius:21,backgroundColor:"#F0F0FF",alignItems:"center",justifyContent:"center"},
  numberText:{fontSize:15,fontWeight:"800",color:INDIGO},
  buttonCopy:{flex:1,marginLeft:14},
  buttonTitle:{fontSize:17,fontWeight:"800",color:"#222228"},
  buttonSubtitle:{fontSize:12,color:"#777781",marginTop:4},
  arrow:{fontSize:28,color:"#9999A3",paddingHorizontal:8},
  info:{backgroundColor:"#FFF",borderRadius:20,padding:18,marginTop:24},
  infoTitle:{fontSize:14,fontWeight:"800",color:"#222228"},
  infoText:{fontSize:12,color:"#777781",marginTop:6},
  nav:{position:"absolute",left:10,right:10,bottom:10,height:68,borderRadius:22,backgroundColor:"rgba(255,255,255,0.98)",flexDirection:"row",alignItems:"center",justifyContent:"space-around",elevation:8,shadowOpacity:.08,shadowRadius:18,shadowOffset:{width:0,height:6}},
  navItem:{alignItems:"center",justifyContent:"center",paddingHorizontal:6},
  navText:{fontSize:10,color:"#888890",fontWeight:"600"},
  active:{color:INDIGO,fontWeight:"800"}
});
