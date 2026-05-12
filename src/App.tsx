import { useState, useEffect, useRef } from 'react';
import { Music, Play, Pause, List, ArrowUp, ArrowDown, Type } from 'lucide-react';

export default function App() {
  const [songs, setSongs] = useState([
    "📁 [תיקיית מוזיקה]",
    "📁 [הורדות]",
    "🎵 שיר 1.mp3",
    "🎵 שיר 2.mp3",
    "🎵 שיר 3.mp3",
  ]);

  const [selectedIndex, setSelectedIndex] = useState(0);
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentSong, setCurrentSong] = useState(null);
  const [isShuffle, setIsShuffle] = useState(false);
  
  const listRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'ArrowDown') {
        setSelectedIndex(prev => (prev + 1) % songs.length);
      } else if (e.key === 'ArrowUp') {
        setSelectedIndex(prev => (prev - 1 + songs.length) % songs.length);
      } else if (e.key === 'Enter') {
        const item = songs[selectedIndex];
        if (item.startsWith('📁')) {
          // Simulate entering folder
          setSongs(["📁 .. [חזור]", "🎵 שיר חדש 1.mp3", "🎵 שיר חדש 2.mp3"]);
          setSelectedIndex(0);
        } else {
          setCurrentSong(item);
          setIsPlaying(true);
        }
      } else if (e.key === '5') {
        setIsShuffle(!isShuffle);
        setCurrentSong(songs[2]); // Simulate shuffle start
        setIsPlaying(true);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [songs, selectedIndex, isPlaying, currentSong]);

  useEffect(() => {
    // Auto-scroll to selected item simulation
    const selectedElement = document.getElementById(`song-${selectedIndex}`);
    if (selectedElement) {
      selectedElement.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    }
  }, [selectedIndex]);

  return (
    <div className="flex flex-col h-screen bg-[#000000] text-white font-sans overflow-hidden">
      {/* Status Bar Simulation */}
      <div className="bg-[#000000] px-4 py-1 flex justify-between items-center text-xs text-gray-500 border-b border-white/5">
        <span>18:45</span>
        <div className="flex space-x-2">
          <span>Signal</span>
          <span>98%</span>
        </div>
      </div>

      {/* Header */}
      <div className="bg-[#000000] p-4 flex items-center shrink-0">
        <Music className="mr-3 text-[#00B0FF]" size={24} />
        <h1 className="text-2xl font-black tracking-tighter">PREMIUM PLAYER</h1>
      </div>

      {/* Song List */}
      <div className="flex-1 overflow-y-auto scrollbar-hide py-2 px-2" ref={listRef}>
        {songs.map((song, index) => (
          <div
            key={index}
            id={`song-${index}`}
            className={`
              mb-1 p-4 rounded-lg flex items-center transition-all duration-200
              ${selectedIndex === index ? 'bg-[#00B0FF] text-white' : 'bg-[#121212]'}
            `}
          >
            <div className={`
              w-10 h-10 rounded-full flex items-center justify-center mr-4 shrink-0
              ${selectedIndex === index ? 'bg-white text-[#00B0FF]' : 'bg-black text-gray-500'}
            `}>
              {currentSong === song && isPlaying ? (
                <div className="flex space-x-0.5 items-end h-4">
                  <div className="w-1 bg-current animate-bounce h-2" />
                  <div className="w-1 bg-current animate-bounce h-4 delay-75" />
                  <div className="w-1 bg-current animate-bounce h-3 delay-150" />
                </div>
              ) : (
                <span className="text-sm font-bold">{index + 1}</span>
              )}
            </div>
            <div className="flex-1 min-w-0">
              <p className={`text-xl font-bold truncate ${selectedIndex === index ? 'text-white' : 'text-white'}`}>
                {song}
              </p>
              <p className={`text-xs truncate ${selectedIndex === index ? 'text-white/80' : 'text-gray-500'}`}>
                תיקייה • {song.startsWith('🎵') ? '8.4MB' : '12 קבצים'}
              </p>
            </div>
          </div>
        ))}
      </div>

      {/* Player Footer / Control Indicator */}
      {currentSong && (
        <div className="bg-[#121212] p-5 border-t border-white/5 shrink-0 rounded-t-2xl">
          <div className="flex items-center space-x-4">
            <div className="bg-[#00B0FF] p-3 rounded-xl text-white shadow-lg shadow-[#00B0FF]/20">
              {isPlaying ? <Pause size={24} fill="currentColor" /> : <Play size={24} fill="currentColor" />}
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex justify-between items-center mb-1">
                <p className="text-[10px] text-[#00B0FF] font-black uppercase tracking-[0.2em]">
                  {isPlaying ? "PLAYING" : "PAUSED"}
                </p>
                {isShuffle && (
                  <span className="text-[10px] bg-[#00B0FF]/20 text-[#00B0FF] px-2 py-0.5 rounded-full font-bold">SHUFFLE</span>
                )}
              </div>
              <p className="text-xl font-black truncate tracking-tight">{currentSong.replace('🎵 ', '')}</p>
              
              {/* Progress UI simulation for preview */}
              <div className="mt-3">
                <div className="w-full bg-white/10 h-0.5 rounded-full overflow-hidden">
                  <div className="bg-[#00B0FF] h-full w-1/3 shadow-[0_0_8px_#00B0FF]" />
                </div>
                <div className="flex justify-between text-[10px] text-gray-500 font-mono mt-1">
                  <span>01:23</span>
                  <span>04:56</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Navigation Help */}
      <div className="bg-black px-4 py-2 text-center text-[10px] text-gray-600 font-bold uppercase tracking-widest shrink-0">
        [5] SHUFFLE • [7] RENAME • [9] VOL • [*] TIME
      </div>
    </div>
  );
}
