import React, { useEffect, useRef, useCallback } from 'react';
import { Editor } from '@monaco-editor/react';

// 🎯 FIX: Renamed 'setCode' to 'onCodeChange' to match the parent component's prop
const CollabEditor = ({ sessionId, currentUserId, code, onCodeChange, language, stompClient }) => { 
  const editorRef = useRef(null);
  
  // --- STOMP Logic: Subscription Setup ---
  useEffect(() => {
    if (!stompClient || !stompClient.connected) {
      console.log("CollabEditor: STOMP client not connected yet. Skipping subscription.");
      return;
    }

    // Subscribe to edits
    const editsSubscription = stompClient.subscribe(`/topic/session/${sessionId}/edits`, (message) => {
      const edit = JSON.parse(message.body);
      if (edit.userId !== currentUserId) {
        applyEdit(edit);
      }
    });

    // Subscribe to chat (optional)
    const chatSubscription = stompClient.subscribe(`/topic/session/${sessionId}/chat`, (message) => {
      const chat = JSON.parse(message.body);
      console.log('Chat received:', chat);
    });

    return () => {
      // Clean up subscriptions on unmount or client change
      editsSubscription.unsubscribe();
      chatSubscription.unsubscribe();
    };
  }, [stompClient, sessionId, currentUserId]);


  // Function to send edits (memoized for stability)
  const sendEdit = useCallback((newCode) => {
    if(stompClient && stompClient.connected) { 
      const editMessage = {
        // 🎯 FIX: Send the structure the backend expects (EditMessage DTO)
        sessionId,
        userId: currentUserRef.current, // Use ref here for the current user ID
        // The backend `EditMessage` DTO likely expects an 'op' object
        op: {
            type: 'replace',
            text: newCode, // Send the full code as the 'text' of the operation
        }
      };
      // Use the modern publish API
      stompClient.publish({ 
        destination: `/app/session/${sessionId}/edit`, 
        body: JSON.stringify(editMessage) 
      });
    }
}, [stompClient, sessionId]);


  // Apply incoming edits (naive replace for now)
  const applyEdit = (edit) => {
    // 🎯 FIX: Use the onCodeChange prop (or call a new prop if parent passes setCode)
    // Assuming the parent component (SessionRoom) expects to manage the state with this call.
    onCodeChange(edit.op.text || ''); 
  };

  // Handle local editor changes
  const handleEditorChange = (value) => {
    // 🎯 FIX: Call the correct prop name: onCodeChange
    onCodeChange(value);
    
    // Note: Since onCodeChange in the parent already handles both local update (setCode)
    // and network broadcast (sendEdit), we don't strictly need this sendEdit call here,
    // as it would be redundant. We'll remove the redundant sendEdit for clean code:
    // sendEdit({ type: 'replace', text: value }); 
    // Wait, since the parent is only responsible for the top level setCode, 
    // and the editor needs to send the network op, we should keep a version of 
    // collaboration here. 
    
    // Let's keep the existing logic, assuming the parent's `handleCodeChange`
    // is structured like: setCode() then sendEdit(). The Monaco `onChange` will
    // trigger this function, which now correctly calls `onCodeChange` (which does
    // setCode and sendEdit in the parent).
    
    // If you want the CollabEditor to be completely self-contained for sending edits, 
    // you would do:
    // onCodeChange(value); // updates local state in parent
    // sendEdit({ type: 'replace', text: value }); // sends op from here
    
    // The previous structure in SessionRoom:
    // const handleCodeChange = (newCode) => { setCode(newCode); sendEdit(newCode); };
    // means the CollabEditor only needs to call `onCodeChange(value)`.
    // The `sendEdit` inside `handleEditorChange` is REDUNDANT based on the parent's design.
    
    // Let's simplify and make the component rely only on the prop, as intended:
    // ✅ SIMPLIFIED: Rely on the parent's handler to perform both local update and network publish
  };

  // We should still define the logic for the Monaco Editor's onChange.
  // We will call the prop `onCodeChange` which is passed from the parent.
  const handleMonacoChange = (value) => {
      onCodeChange(value);
  }

  return (
    <Editor
      height="100%"
      language={language}
      theme="vs-dark"
      value={code}
      // 🎯 FIX: Pass the new simple handler to the Editor's onChange
      onChange={handleMonacoChange}
      onMount={(editor) => (editorRef.current = editor)}
    />
  );
};

export default CollabEditor;