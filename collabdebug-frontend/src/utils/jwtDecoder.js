// src/utils/jwtDecoder.js
// Utility to decode JWT token and extract user information

/**
 * Decodes a JWT token to extract the payload
 * @param {string} token - The JWT token
 * @returns {object|null} - The decoded payload or null if invalid
 */
export function decodeToken(token) {
  try {
    if (!token) return null;
    
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    
    // Decode the payload (second part)
    const payload = parts[1];
    const decoded = JSON.parse(atob(payload));
    return decoded;
  } catch (err) {
    console.error('Failed to decode JWT token:', err);
    return null;
  }
}

/**
 * Extracts username from JWT token
 * @param {string} token - The JWT token
 * @returns {string|null} - The username or null if invalid
 */
export function getUsername(token) {
  const decoded = decodeToken(token);
  return decoded?.sub || null; // JWT's 'sub' claim typically contains the username
}

/**
 * Checks if JWT token is expired
 * @param {string} token - The JWT token
 * @returns {boolean} - True if expired, false otherwise
 */
export function isTokenExpired(token) {
  const decoded = decodeToken(token);
  if (!decoded || !decoded.exp) return true;
  
  const currentTime = Math.floor(Date.now() / 1000);
  return decoded.exp < currentTime;
}
