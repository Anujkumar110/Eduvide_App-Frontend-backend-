#!/bin/bash

# WebRTC Diagnostic Script for EduVibe
# This script tests the complete WebRTC flow

echo "=================================="
echo "EduVibe WebRTC Diagnostic Tool"
echo "=================================="
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
BACKEND_URL="http://localhost:5000"
API_URL="$BACKEND_URL/api/v1"

echo "Step 1: Checking Backend Server..."
echo "-----------------------------------"

# Check if backend is running
if curl -s "$API_URL/health" > /dev/null 2>&1; then
    echo -e "${GREEN}✅ Backend server is running${NC}"
else
    echo -e "${RED}❌ Backend server is NOT running!${NC}"
    echo "   Start it with: cd backend-eduvibe-app-2.0 && npm run dev"
    exit 1
fi

echo ""
echo "Step 2: Testing WebRTC ICE Config Endpoint..."
echo "----------------------------------------------"

ICE_RESPONSE=$(curl -s "$API_URL/config/webrtc")
if echo "$ICE_RESPONSE" | grep -q "iceServers"; then
    echo -e "${GREEN}✅ ICE config endpoint working${NC}"
    ICE_COUNT=$(echo "$ICE_RESPONSE" | grep -o "\"urls\":" | wc -l)
    echo "   Found $ICE_COUNT ICE servers"
else
    echo -e "${YELLOW}⚠️  ICE config endpoint not responding correctly${NC}"
    echo "   Response: $ICE_RESPONSE"
fi

echo ""
echo "Step 3: Checking WebSocket Server..."
echo "-------------------------------------"

# WebSocket test - just check if port is open
if nc -z localhost 5000; then
    echo -e "${GREEN}✅ WebSocket port (5000) is open${NC}"
else
    echo -e "${RED}❌ WebSocket port (5000) is NOT accessible!${NC}"
    exit 1
fi

echo ""
echo "Step 4: Testing Authentication..."
echo "----------------------------------"

echo "Note: You need to login first to get a token"
echo "Please provide your auth token (from SharedPreferences 'eduvibe_prefs'):"
read -p "Enter your JWT token: " AUTH_TOKEN

if [ -z "$AUTH_TOKEN" ]; then
    echo -e "${YELLOW}⚠️  No token provided - skipping auth test${NC}"
else
    # Test WebSocket connection with token
    WS_URL="ws://localhost:5000?token=$AUTH_TOKEN"
    echo "Testing WebSocket connection..."
    
    # Use wscat if available
    if command -v wscat &> /dev/null; then
        echo "Connecting to: $WS_URL"
        timeout 5 wscat -c "$WS_URL" --no-check <<EOF
{"type":"room:join","payload":{"classId":"test-room","role":"teacher"}}
EOF
    else
        echo -e "${YELLOW}⚠️  wscat not installed${NC}"
        echo "   Install with: npm install -g wscat"
        echo "   Or test manually from Android app"
    fi
fi

echo ""
echo "=================================="
echo "Diagnostic Complete!"
echo "=================================="
echo ""
echo "Next Steps:"
echo "1. Check Android LogCat for WebRTC logs"
echo "2. Filter by tags: 'WebRTCClient', 'SignalingClient', 'CallViewModel'"
echo "3. Look for these success indicators:"
echo "   - 'startAsTeacher()' or 'startAsViewer()'"
echo "   - 'Local media started ✅'"
echo "   - 'Connected + joinRoom'"
echo "   - 'Offer sent' / 'Answer received'"
echo "   - 'Remote video sink attached ✅'"
echo ""
echo "If you see errors, share the logs for debugging."
