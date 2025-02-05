import { Dialog, DialogContent, DialogProps } from '@mui/material';
import { ServiceTabs } from '../payment/services/ServiceTabs';
import { useMobile } from '../../utils/hooks/useMobile';
import { useSearchParams } from 'react-router-dom';

export type UsefulComposerActionDialogProps = DialogProps;

export default function UsefulComposerActionDialog({ ...props }: UsefulComposerActionDialogProps) {
  const isMobile = useMobile();
  const tab = useSearchParams()[0].get('tab') ?? undefined;

  return (
    <Dialog
      disableEnforceFocus
      fullScreen={isMobile}
      {...props}
      PaperProps={{
        elevation: 5,
        sx: {
          ...(!isMobile && {
            width: 375,
            borderRadius: 5
          })
        }
      }}
      sx={{
        backdropFilter: 'blur(3px)'
      }}>
      <DialogContent>
        <ServiceTabs tab={tab} />
      </DialogContent>
    </Dialog>
  );
}
