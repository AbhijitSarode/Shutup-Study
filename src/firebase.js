import { initializeApp } from "firebase/app";
import { getFirestore, serverTimestamp } from "firebase/firestore";

const firebaseConfig = {
  apiKey: "AIzaSyCC25abhQkLlep2xe926x1_KK1yWa_dfrE",
  authDomain: "shutupnstudy-1734a.firebaseapp.com",
  projectId: "shutupnstudy-1734a",
  storageBucket: "shutupnstudy-1734a.firebasestorage.app",
  messagingSenderId: "545877201348",
  appId: "1:545877201348:web:e4d6e195643d3f15371491"
};

// Initialize Firebase
const app = initializeApp(firebaseConfig);
const db = getFirestore(app);

export { db, serverTimestamp };
