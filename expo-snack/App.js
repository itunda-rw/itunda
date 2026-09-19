import React, { useState } from "react";
import { SafeAreaView, View, Text, Pressable, StyleSheet, ScrollView } from "react-native";

const INDIGO = "#7472F4";
const screens = ["Home", "Identity", "Chat", "Marketplace", "Mini Apps"];

const faces = [
  ["♥️", "Love", "#EF4A63"], ["😆", "Laugh", "#FFCC4D"],
  ["😮", "Wow", "#FFD39A"], ["😢", "Sad", "#9CC8FF"], ["👍", "Like", "#FFD39A"],
];

function Header({title}) {
  return <View style={styles.header}><View><Text style={styles.eyebrow}>ITUNDA</Text><Text style={styles.title}>{title}</Text></View><View style={styles.avatar}><Text style={styles.avatarText}>E</Text></View></View>;
}
function Home({go}) {
  return <>
    <View style={styles.hero}><Text style={styles.heroEyebrow}>WELCOME TO ITUNDA</Text><Text style={styles.heroTitle}>Everything you need,</Text><Text style={styles.heroTitle}>in one place.</Text><Text style={styles.heroBody}>Simple services for everyday life in Rwanda.</Text></View>
    <Text style={styles.section}>Quick actions</Text>
    <View style={styles.grid}>{[["◉","Identity"],["◌","Chat"],["◇","Marketplace"],["✦","Mini Apps"]].map(([icon,label])=><Pressable key={label} style={styles.card} onPress={()=>go(label)}><Text style={styles.cardIcon}>{icon}</Text><Text style={styles.cardTitle}>{label}</Text><Text style={styles.muted}>Open</Text></Pressable>)}</View>
    <View style={styles.faceCard}><Text style={styles.section}>ItundaFace</Text><Text style={styles.muted}>Original Itunda expression system</Text><ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.faceRow}>{faces.map(([emoji,label,color])=><View key={label} style={[styles.face,{backgroundColor:color}]}><Text style={styles.faceText}>{emoji}</Text></View>)}</ScrollView></View>
  </>;
}
function Panel({type}) {
  const data={Identity:["◉","Itunda Identity","One identity layer for Itunda services."],Chat:["◌","Chat","Fast, simple communication inside Itunda."],Marketplace:["◇","Marketplace","Discover nearby products, services and opportunities."],"Mini Apps":["✦","Mini Apps","Small focused experiences inside the Itunda shell."]}[type];
  return <View style={styles.panel}><Text style={styles.panelIcon}>{data[0]}</Text><Text style={styles.panelTitle}>{data[1]}</Text><Text style={styles.muted}>{data[2]}</Text>
    {type==="Identity"&&<><View style={styles.status}><Text style={styles.statusDot}>●</Text><Text style={styles.statusText}>Identity ready</Text></View><Pressable style={styles.primary}><Text style={styles.primaryText}>View identity</Text></Pressable></>}
    {type==="Chat"&&<><View style={styles.chatBubble}><Text>Welcome to Itunda 👋</Text></View><View style={[styles.chatBubble,styles.right]}><Text>Hello!</Text></View></>}
    {type==="Marketplace"&&["🥬 Fresh market","🛠️ Local services"].map(x=><View key={x} style={styles.listCard}><Text style={styles.listTitle}>{x}</Text></View>)}
    {type==="Mini Apps"&&<View style={styles.miniGrid}>{["🍲 Food","💼 Jobs","🎮 Games","🚲 Delivery"].map(x=><Pressable key={x} style={styles.mini}><Text style={styles.listTitle}>{x}</Text></Pressable>)}</View>}
  </View>;
}
export default function App(){
  const [screen,setScreen]=useState("Home");
  return <SafeAreaView style={styles.safe}><View style={styles.app}><ScrollView contentContainerStyle={styles.content}><Header title={screen}/>{screen==="Home"?<Home go={setScreen}/>:<Panel type={screen}/>}</ScrollView><View style={styles.nav}>{screens.map(x=><Pressable key={x} style={styles.navItem} onPress={()=>setScreen(x)}><Text style={[styles.navIcon,screen===x&&styles.active]}>{x==="Home"?"⌂":x==="Identity"?"◉":x==="Chat"?"◌":x==="Marketplace"?"◇":"✦"}</Text><Text style={[styles.navText,screen===x&&styles.active]}>{x}</Text></Pressable>)}</View></View></SafeAreaView>;
}
const styles=StyleSheet.create({
safe:{flex:1,backgroundColor:"#F7F7FA"},app:{flex:1},content:{padding:20,paddingBottom:120},
header:{flexDirection:"row",justifyContent:"space-between",alignItems:"center",marginBottom:24},eyebrow:{fontSize:12,fontWeight:"800",letterSpacing:1.5,color:INDIGO},title:{fontSize:32,fontWeight:"800",color:"#111116",marginTop:3},avatar:{width:42,height:42,borderRadius:21,backgroundColor:"#ECECFF",alignItems:"center",justifyContent:"center"},avatarText:{fontWeight:"800",color:INDIGO},
hero:{backgroundColor:INDIGO,borderRadius:28,padding:26,marginBottom:28},heroEyebrow:{fontSize:11,fontWeight:"800",letterSpacing:1.3,color:"#ECECFF",marginBottom:14},heroTitle:{fontSize:28,lineHeight:32,fontWeight:"800",color:"#FFF"},heroBody:{fontSize:15,lineHeight:22,color:"#ECECFF",marginTop:16},section:{fontSize:19,fontWeight:"800",color:"#111116"},muted:{fontSize:13,color:"#777781",marginTop:5},
grid:{flexDirection:"row",flexWrap:"wrap",gap:10,marginTop:14,marginBottom:28},card:{width:"48%",minHeight:105,borderRadius:20,backgroundColor:"#FFF",padding:16,justifyContent:"space-between"},cardIcon:{fontSize:28,color:INDIGO},cardTitle:{fontSize:16,fontWeight:"800",color:"#222228"},
faceCard:{backgroundColor:"#FFF",borderRadius:26,padding:20},faceRow:{gap:10,paddingTop:18},face:{width:66,height:66,borderRadius:33,alignItems:"center",justifyContent:"center"},faceText:{fontSize:36},
panel:{backgroundColor:"#FFF",borderRadius:28,padding:26,minHeight:500},panelIcon:{fontSize:48,color:INDIGO,marginBottom:18},panelTitle:{fontSize:28,fontWeight:"800",color:"#111116"},status:{flexDirection:"row",alignItems:"center",marginTop:24},statusDot:{color:"#31B56A",fontSize:12},statusText:{marginLeft:8,fontWeight:"700"},primary:{backgroundColor:INDIGO,borderRadius:16,padding:16,alignItems:"center",marginTop:24},primaryText:{color:"#FFF",fontWeight:"800"},chatBubble:{alignSelf:"flex-start",backgroundColor:"#F0F0FF",padding:14,borderRadius:18,marginTop:24},right:{alignSelf:"flex-end",backgroundColor:"#EDEDED"},listCard:{backgroundColor:"#F7F7FA",borderRadius:18,padding:18,marginTop:14},listTitle:{fontSize:15,fontWeight:"800",color:"#222228"},miniGrid:{flexDirection:"row",flexWrap:"wrap",gap:10,marginTop:20},mini:{width:"48%",backgroundColor:"#F7F7FA",borderRadius:18,padding:18},
nav:{position:"absolute",left:10,right:10,bottom:10,height:74,borderRadius:24,backgroundColor:"rgba(255,255,255,0.98)",flexDirection:"row",alignItems:"center",justifyContent:"space-around",elevation:8,shadowOpacity:.08,shadowRadius:18,shadowOffset:{width:0,height:6}},navItem:{alignItems:"center",justifyContent:"center",minWidth:62},navIcon:{fontSize:20,color:"#888890"},navText:{fontSize:9,color:"#888890",fontWeight:"600",marginTop:4},active:{color:INDIGO,fontWeight:"800"}
});