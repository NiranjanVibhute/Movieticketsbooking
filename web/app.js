// Dynamic Backend URL resolver:
// - Direct Render/Localhost: uses relative path "" (same-origin).
// - Vercel / External hosts: automatically routes to https://movieticketsbooking.onrender.com
const BACKEND_URL = (() => {
  if (typeof window !== 'undefined') {
    if (window.API_BACKEND_URL) return window.API_BACKEND_URL;
    const stored = localStorage.getItem('BACKEND_URL');
    if (stored) return stored;

    const hostname = window.location.hostname;
    if (hostname === 'localhost' || hostname === '127.0.0.1' || hostname.includes('onrender.com')) {
      return "";
    }
    // Default backend URL for Vercel deployment
    return "https://movieticketsbooking.onrender.com";
  }
  return "https://movieticketsbooking.onrender.com";
})();

const API = {
  getMovies: async () => {
    const res = await fetch(`${BACKEND_URL}/api/movies`);
    return await res.json();
  },

  getMovie: async (id) => {
    const res = await fetch(`${BACKEND_URL}/api/movies/${id}`);
    return await res.json();
  },

  getSeats: async (movieId, showTime) => {
    const res = await fetch(`${BACKEND_URL}/api/seats?movieId=${encodeURIComponent(movieId)}&showTime=${encodeURIComponent(showTime)}`);
    return await res.json();
  },

  getVibeHeatmap: async (movieId, showTime) => {
    const res = await fetch(`${BACKEND_URL}/api/seats/heatmap?movieId=${encodeURIComponent(movieId)}&showTime=${encodeURIComponent(showTime)}`);
    return await res.json();
  },

  optimizeSeats: async (payload) => {
    const res = await fetch(`${BACKEND_URL}/api/seats/optimize`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    return await res.json();
  },

  lockSeats: async (seatIds, durationMinutes = 10) => {
    const res = await fetch(`${BACKEND_URL}/api/seats/lock`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ seatIds, durationMinutes })
    });
    return await res.json();
  },

  createSplitPay: async (payload) => {
    const res = await fetch(`${BACKEND_URL}/api/split-pay/create`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    return await res.json();
  },

  getSplitStatus: async (code) => {
    const res = await fetch(`${BACKEND_URL}/api/split-pay/status?code=${encodeURIComponent(code)}`);
    return await res.json();
  },

  contributeSplitPay: async (code, participantName) => {
    const res = await fetch(`${BACKEND_URL}/api/split-pay/pay`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code, participantName })
    });
    return await res.json();
  },

  getSnacks: async () => {
    const res = await fetch(`${BACKEND_URL}/api/snacks`);
    return await res.json();
  },

  calculateSnack: async (baseType, addOns) => {
    const res = await fetch(`${BACKEND_URL}/api/snacks/calculate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ baseType, addOns })
    });
    return await res.json();
  },

  checkout: async (payload) => {
    const res = await fetch(`${BACKEND_URL}/api/bookings/checkout`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    return await res.json();
  },

  getBooking: async (bookingRef) => {
    const res = await fetch(`${BACKEND_URL}/api/bookings/${encodeURIComponent(bookingRef)}`);
    return await res.json();
  }
};

// UI Toast Notification Utility
function showToast(message, type = 'info') {
  let container = document.getElementById('toast-container');
  if (!container) {
    container = document.createElement('div');
    container.id = 'toast-container';
    container.className = 'toast-container';
    document.body.appendChild(container);
  }

  const toast = document.createElement('div');
  toast.className = 'toast';
  const icon = type === 'success' ? '⚡' : type === 'warning' ? '⚠️' : 'ℹ️';
  toast.innerHTML = `<span style="font-size:1.2rem;">${icon}</span><span>${message}</span>`;

  container.appendChild(toast);
  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateY(10px)';
    toast.style.transition = 'all 0.3s ease';
    setTimeout(() => toast.remove(), 300);
  }, 4000);
}

// Session State Store
const CinemaStore = {
  getCurrentSelection: () => {
    try {
      const data = sessionStorage.getItem('darkops_current_booking');
      return data ? JSON.parse(data) : null;
    } catch (e) { return null; }
  },

  setCurrentSelection: (obj) => {
    sessionStorage.setItem('darkops_current_booking', JSON.stringify(obj));
  },

  getSnackOrder: () => {
    try {
      const data = sessionStorage.getItem('darkops_snack_order');
      return data ? JSON.parse(data) : [];
    } catch (e) { return []; }
  },

  setSnackOrder: (snacks) => {
    sessionStorage.setItem('darkops_snack_order', JSON.stringify(snacks));
  },

  getLastBooking: () => {
    try {
      const data = sessionStorage.getItem('darkops_last_booking');
      return data ? JSON.parse(data) : null;
    } catch (e) { return null; }
  },

  setLastBooking: (booking) => {
    sessionStorage.setItem('darkops_last_booking', JSON.stringify(booking));
  }
};

// Simple SVG Barcode Generator for confirmation mockup
function generateBarcodeSVG(codeText) {
  const bars = [];
  let x = 10;
  for (let i = 0; i < codeText.length; i++) {
    const charCode = codeText.charCodeAt(i);
    const w1 = (charCode % 3) + 1;
    const w2 = ((charCode >> 1) % 3) + 1;
    bars.push(`<rect x="${x}" y="5" width="${w1 * 2}" height="50" fill="#000" />`);
    x += (w1 * 2) + 2;
    bars.push(`<rect x="${x}" y="5" width="${w2}" height="50" fill="#000" />`);
    x += w2 + 3;
  }
  return `<svg class="barcode-svg" viewBox="0 0 ${x + 20} 60" xmlns="http://w3.org">
    <rect width="100%" height="100%" fill="#ffffff"/>
    ${bars.join('')}
    <text x="${(x + 20) / 2}" y="58" font-family="monospace" font-size="9" text-anchor="middle" fill="#000">${codeText}</text>
  </svg>`;
}